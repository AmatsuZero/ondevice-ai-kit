#include "WeMMNative.h"

#include <TargetConditionals.h>

#include "ggml-backend.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "nlohmann/json.hpp"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <limits>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
using json = nlohmann::json;
namespace fs = std::filesystem;

std::atomic_flag run_lock = ATOMIC_FLAG_INIT;
constexpr char kError[] = "WeMM generation failed";
constexpr char kEmpty[] = "";

struct RunLock {
    RunLock() = default;
    RunLock(const RunLock &) = delete;
    RunLock &operator=(const RunLock &) = delete;
    ~RunLock() { run_lock.clear(std::memory_order_release); }
};

struct BackendGuard {
    BackendGuard() {
        ggml_backend_load_all();
        llama_backend_init();
    }
    BackendGuard(const BackendGuard &) = delete;
    BackendGuard &operator=(const BackendGuard &) = delete;
    ~BackendGuard() { llama_backend_free(); }
};

[[noreturn]] void fail() {
    throw std::runtime_error("native operation failed");
}

void require(bool condition) {
    if (!condition) fail();
}

struct Cancelled {};

llama_pos checked_image_position(uint32_t position) {
    require(static_cast<uint64_t>(position) <=
            static_cast<uint64_t>(std::numeric_limits<llama_pos>::max()));
    return static_cast<llama_pos>(position);
}

bool checkpoint(void *context, odai_wemm_checkpoint callback,
                odai_wemm_stage stage, int generated,
                const std::string *bytes = nullptr) {
    if (!callback) return false;
    const char *data = bytes ? bytes->data() : kEmpty;
    const size_t size = bytes ? bytes->size() : 0;
    try {
        return callback(context, stage, generated, data, size) != 0;
    } catch (...) {
        fail();
    }
}

bool within_directory(const fs::path &directory, const fs::path &file) {
    auto d = directory.begin();
    auto f = file.begin();
    for (; d != directory.end(); ++d, ++f) {
        if (f == file.end() || *d != *f) return false;
    }
    return f != file.end();
}

std::string checked_media_path(const json &media, const fs::path &directory,
                               size_t index) {
    require(media.is_object());
    require(media.value("video_frame", false));
    require(media.value("id", std::string()) == "hf-video+" + std::to_string(index));
    require(media.contains("path") && media.at("path").is_string());
    const fs::path supplied(media.at("path").get<std::string>());
    require(supplied.is_absolute());
    const fs::path resolved = fs::canonical(supplied);
    require(fs::is_regular_file(resolved));
    require(within_directory(directory, resolved));
    return resolved.string();
}

int checked_budget(const json &request) {
    require(request.contains("max_new_tokens"));
    const auto &budget = request.at("max_new_tokens");
    require(budget.is_number_integer());
    if (budget.is_number_unsigned()) {
        const auto value = budget.get<json::number_unsigned_t>();
        require(value > 0 && value <= static_cast<json::number_unsigned_t>(
                                            std::numeric_limits<int>::max()));
        return static_cast<int>(value);
    }
    const auto value = budget.get<json::number_integer_t>();
    require(value > 0 && value <= std::numeric_limits<int>::max());
    return static_cast<int>(value);
}

json read_request(const fs::path &directory,
                  std::vector<std::string> &frame_paths) {
    require(fs::is_directory(directory));
    const fs::path resolved_directory = fs::canonical(directory);
    const fs::path request_path = resolved_directory / "request.json";
    std::ifstream input(request_path);
    require(input.is_open());
    json request;
    input >> request;
    require(input.good() || input.eof());
    require(request.is_object());
    require(request.contains("prompt") && request.at("prompt").is_string());
    (void) checked_budget(request);
    require(request.value("native_input_protocol", std::string()) ==
            "hf_uniform_video_v1");
    require(request.contains("native_prepared_media") &&
            request.at("native_prepared_media").is_array() &&
            request.at("native_prepared_media").size() == 8);
    require(request.contains("media") && request.at("media").is_array() &&
            request.at("media").empty());
    require(request.contains("native_video_metadata") &&
            request.at("native_video_metadata").is_object());
    const auto &metadata = request.at("native_video_metadata");
    require(metadata.contains("sampled_frames") &&
            metadata.at("sampled_frames").is_number_integer() &&
            metadata.at("sampled_frames") == 8);
    require(!request.contains("strict_final_choice"));
    require(!request.contains("canonical_frames"));
    require(!request.contains("raw_video"));
    require(!request.contains("raw_audio"));
    require(!request.contains("audio_projector"));

    frame_paths.reserve(8);
    for (size_t i = 0; i < 8; ++i) {
        frame_paths.push_back(checked_media_path(
            request.at("native_prepared_media").at(i), resolved_directory, i));
    }
    return request;
}

std::string token_piece(const llama_vocab *vocab, llama_token token) {
    std::vector<char> buffer(256);
    int size = llama_token_to_piece(vocab, token, buffer.data(),
                                    static_cast<int>(buffer.size()), 0, false);
    if (size < 0) {
        buffer.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(vocab, token, buffer.data(),
                                    static_cast<int>(buffer.size()), 0, false);
    }
    require(size >= 0);
    return std::string(buffer.data(), static_cast<size_t>(size));
}

void publish(void *context, odai_wemm_result callback,
             const char *text, size_t size) {
    try {
        callback(context, text, size);
    } catch (...) {
        // C callbacks must not throw across the ABI boundary.
    }
}

int run(const char *language, const char *projector,
        const char *request_directory, void *context,
        odai_wemm_checkpoint progress, odai_wemm_result result,
        bool &result_called) {
    if (checkpoint(context, progress, ODAI_WEMM_LOAD, 0)) return 3;

    std::vector<std::string> frame_paths;
    json request = read_request(request_directory, frame_paths);
    const std::string prompt = request.at("prompt").get<std::string>();
    const int max_new = checked_budget(request);

    BackendGuard backend;
    if (checkpoint(context, progress, ODAI_WEMM_LOAD, 0)) return 3;

    auto model_params = llama_model_default_params();
#if TARGET_OS_SIMULATOR
    model_params.n_gpu_layers = 0;
#else
    model_params.n_gpu_layers = 99;
#endif
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
        llama_model_load_from_file(language, model_params), llama_model_free);
    require(static_cast<bool>(model));
    if (checkpoint(context, progress, ODAI_WEMM_LOAD, 0)) return 3;

    auto context_params = llama_context_default_params();
#if TARGET_OS_SIMULATOR
    context_params.offload_kqv = false;
    context_params.op_offload = false;
#endif
    context_params.n_ctx = 4096;
    context_params.n_batch = 512;
    context_params.n_ubatch = 128;
    context_params.n_threads = 4;
    context_params.n_threads_batch = 4;
    context_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    std::unique_ptr<llama_context, decltype(&llama_free)> ctx(
        llama_init_from_model(model.get(), context_params), llama_free);
    require(static_cast<bool>(ctx));

    auto vision_params = mtmd_context_params_default();
#if TARGET_OS_SIMULATOR
    vision_params.use_gpu = false;
#else
    vision_params.use_gpu = true;
#endif
    vision_params.n_threads = 4;
    vision_params.image_min_tokens = 4;
    vision_params.image_max_tokens = 768;
    vision_params.warmup = false;
    vision_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    if (checkpoint(context, progress, ODAI_WEMM_LOAD, 0)) return 3;
    std::unique_ptr<mtmd_context, decltype(&mtmd_free)> vision(
        mtmd_init_from_file(projector, model.get(), vision_params), mtmd_free);
    require(static_cast<bool>(vision));
    require(mtmd_support_vision(vision.get()));
    require(mtmd_decode_use_mrope(vision.get()));
    if (checkpoint(context, progress, ODAI_WEMM_LOAD, 0)) return 3;

    using Bitmap = std::unique_ptr<mtmd_bitmap, decltype(&mtmd_bitmap_free)>;
    std::vector<Bitmap> bitmaps;
    bitmaps.reserve(frame_paths.size());
    auto bitmap_options = mtmd_helper_init_opt_default();
    for (const auto &path : frame_paths) {
        if (checkpoint(context, progress, ODAI_WEMM_LOAD, 0)) return 3;
        auto loaded = mtmd_helper_bitmap_init_from_file(
            vision.get(), path.c_str(), false, bitmap_options);
        Bitmap bitmap(loaded.bitmap, mtmd_bitmap_free);
#if defined(MTMD_VIDEO)
        std::unique_ptr<mtmd_helper_video, decltype(&mtmd_helper_video_free)>
            video(loaded.video_ctx, mtmd_helper_video_free);
#else
        require(loaded.video_ctx == nullptr);
#endif
        require(bitmap != nullptr);
#if defined(MTMD_VIDEO)
        require(video == nullptr);
#endif
        mtmd_bitmap_set_mergeable(bitmap.get(), true);
        const std::string id = "hf-video+" + std::to_string(bitmaps.size());
        mtmd_bitmap_set_id(bitmap.get(), id.c_str());
        bitmaps.emplace_back(std::move(bitmap));
    }

    if (checkpoint(context, progress, ODAI_WEMM_PREPARE, 0)) return 3;
    std::vector<mtmd_bitmap *> bitmap_ptrs;
    bitmap_ptrs.reserve(bitmaps.size());
    for (auto &bitmap : bitmaps) bitmap_ptrs.push_back(bitmap.get());
    mtmd_input_text input_text{prompt.data(), prompt.size(), false, true};
    mtmd::input_chunks chunks(mtmd_input_chunks_init());
    require(mtmd_tokenize(vision.get(), chunks.ptr.get(), &input_text,
                          bitmap_ptrs.data(), bitmap_ptrs.size()) == 0);
    bitmaps.clear();
    if (checkpoint(context, progress, ODAI_WEMM_PREPARE, 0)) return 3;

    const size_t chunk_count = mtmd_input_chunks_size(chunks.ptr.get());
    require(chunk_count == 9);
    size_t text_chunks = 0;
    size_t image_chunks = 0;
    size_t input_tokens = 0;
    size_t input_positions = 0;
    for (size_t i = 0; i < chunk_count; ++i) {
        const auto *chunk = mtmd_input_chunks_get(chunks.ptr.get(), i);
        const auto kind = mtmd_input_chunk_get_type(chunk);
        if (i % 2 == 0) {
            require(kind == MTMD_INPUT_CHUNK_TYPE_TEXT);
            ++text_chunks;
        } else {
            require(kind == MTMD_INPUT_CHUNK_TYPE_IMAGE);
            require(!mtmd_decode_use_non_causal(vision.get(), chunk));
            const auto *id = mtmd_input_chunk_get_id(chunk);
            require(id != nullptr && std::string(id).rfind("hf-video+", 0) == 0);
            ++image_chunks;
        }
        const size_t count = mtmd_input_chunk_get_n_tokens(chunk);
        if (kind == MTMD_INPUT_CHUNK_TYPE_TEXT || kind == MTMD_INPUT_CHUNK_TYPE_IMAGE)
            require(count > 0);
        require(count <= std::numeric_limits<size_t>::max() - input_tokens);
        input_tokens += count;
        const llama_pos chunk_positions = mtmd_input_chunk_get_n_pos(chunk);
        require(chunk_positions >= 0);
        require(static_cast<uint64_t>(chunk_positions) <=
                static_cast<uint64_t>(std::numeric_limits<size_t>::max() - input_positions));
        input_positions += static_cast<size_t>(chunk_positions);
    }
    require(input_positions <=
            static_cast<size_t>(std::numeric_limits<llama_pos>::max()));
    require(text_chunks == 5 && image_chunks == 4);
    const size_t context_tokens = static_cast<size_t>(llama_n_ctx(ctx.get()));
    require(input_tokens > 0 && input_tokens <= context_tokens &&
            input_positions <= context_tokens &&
            static_cast<size_t>(max_new) <= context_tokens - input_tokens &&
            static_cast<size_t>(max_new) <= context_tokens - input_positions);

    const auto *vocab = llama_model_get_vocab(model.get());
    const int hidden = llama_model_n_embd_inp(model.get());
    require(vocab != nullptr && hidden > 0);
    llama_memory_clear(llama_get_memory(ctx.get()), true);
    std::unique_ptr<llama_batch_ext, decltype(&llama_batch_ext_free)> batch(
        llama_batch_ext_init(ctx.get()), llama_batch_ext_free);
    require(static_cast<bool>(batch));

    size_t pending_rows = 0;
    size_t rows_added = 0;
    size_t rows_submitted = 0;
    llama_pos past = 0;
    auto submit = [&]() {
        if (pending_rows == 0) return;
        if (checkpoint(context, progress, ODAI_WEMM_PREFILL, 0))
            throw Cancelled{};
        require(llama_process(ctx.get(), LLAMA_PROCESS_TYPE_DECODE,
                              batch.get()) == 0);
        rows_submitted += pending_rows;
        pending_rows = 0;
        llama_batch_ext_clear(batch.get());
        if (checkpoint(context, progress, ODAI_WEMM_PREFILL, 0))
            throw Cancelled{};
    };

    for (size_t i = 0; i < chunk_count; ++i) {
        if (checkpoint(context, progress, ODAI_WEMM_PREFILL, 0)) return 3;
        const auto *chunk = mtmd_input_chunks_get(chunks.ptr.get(), i);
        const auto kind = mtmd_input_chunk_get_type(chunk);
        const size_t count = mtmd_input_chunk_get_n_tokens(chunk);
        if (kind == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t text_count = 0;
            const auto *tokens = mtmd_input_chunk_get_tokens_text(chunk, &text_count);
            require(tokens != nullptr && text_count == count);
            const llama_pos text_positions = mtmd_input_chunk_get_n_pos(chunk);
            require(text_positions >= 0 &&
                    static_cast<size_t>(text_positions) == text_count);
            for (size_t j = 0; j < text_count; ++j) {
                const int32_t row = llama_batch_ext_add_token(batch.get(), 0, tokens[j]);
                require(row >= 0);
                const llama_pos position = past + static_cast<llama_pos>(j);
                require(llama_batch_ext_set_pos(batch.get(), row, &position));
                if (i + 1 == chunk_count && j + 1 == text_count)
                    require(llama_batch_ext_set_output_logits(batch.get(), row, true));
                ++pending_rows;
                ++rows_added;
                if (pending_rows == 512) submit();
            }
            past += static_cast<llama_pos>(mtmd_input_chunk_get_n_pos(chunk));
        } else {
            require(kind == MTMD_INPUT_CHUNK_TYPE_IMAGE);
            require(mtmd_encode_chunk(vision.get(), chunk) == 0);
            float *embeddings = mtmd_get_output_embd(vision.get());
            require(embeddings != nullptr);
            require(count <= std::numeric_limits<size_t>::max() /
                                 static_cast<size_t>(hidden));
            require(std::all_of(embeddings, embeddings + count *
                                static_cast<size_t>(hidden),
                                [](float value) { return std::isfinite(value); }));
            const auto *image_tokens = mtmd_input_chunk_get_tokens_image(chunk);
            require(image_tokens != nullptr);
            for (size_t j = 0; j < count; ++j) {
                const auto image_pos = mtmd_image_tokens_get_decoder_pos(
                    image_tokens, past, j);
                const llama_pos positions[4] = {
                    checked_image_position(image_pos.t),
                    checked_image_position(image_pos.y),
                    checked_image_position(image_pos.x),
                    checked_image_position(image_pos.z)};
                const int32_t row = llama_batch_ext_add_embd(
                    batch.get(), 0,
                    llama_embd{embeddings + j * static_cast<size_t>(hidden),
                               1, static_cast<size_t>(hidden)});
                require(row >= 0);
                require(llama_batch_ext_set_pos(batch.get(), row, positions));
                ++pending_rows;
                ++rows_added;
                if (pending_rows == 512) submit();
            }
            past += static_cast<llama_pos>(mtmd_input_chunk_get_n_pos(chunk));
        }
        if (checkpoint(context, progress, ODAI_WEMM_PREFILL, 0)) return 3;
    }
    submit();
    require(pending_rows == 0 && rows_added == input_tokens &&
            rows_submitted == input_tokens &&
            static_cast<size_t>(past) == input_positions);

    std::string output;
    int generated = 0;
    const int vocab_size = llama_vocab_n_tokens(vocab);
    require(vocab_size > 0);
    for (int step = 0; step < max_new; ++step) {
        const float *logits = llama_get_logits_ith(ctx.get(), -1);
        require(logits != nullptr &&
                std::all_of(logits, logits + vocab_size,
                            [](float value) { return std::isfinite(value); }));
        llama_token token = static_cast<llama_token>(
            std::max_element(logits, logits + vocab_size) - logits);
        if (llama_vocab_is_eog(vocab, token)) break;
        output += token_piece(vocab, token);
        ++generated;
        if (checkpoint(context, progress, ODAI_WEMM_GENERATE,
                       generated, &output)) return 3;
        if (step + 1 == max_new) break;

        llama_pos positions[4] = {past, past, past, 0};
        ++past;
        int32_t sequence_count = 1;
        llama_seq_id sequence_id = 0;
        llama_seq_id *sequence = &sequence_id;
        int8_t output_logits = 1;
        llama_batch decode_batch{1, &token, nullptr,
                                 positions, &sequence_count, &sequence,
                                 &output_logits};
        require(llama_decode(ctx.get(), decode_batch) == 0);
    }

    if (checkpoint(context, progress, ODAI_WEMM_GENERATE, generated, &output)) return 3;
    result_called = true;
    publish(context, result, output.data(), output.size());
    return 0;
}
} // namespace

extern "C" int odai_wemm_generate(
    const char *language, const char *projector, const char *request_directory,
    void *context, odai_wemm_checkpoint progress, odai_wemm_result result) {
    if (!language || !projector || !request_directory || !result ||
        language[0] == '\0' || projector[0] == '\0' || request_directory[0] == '\0') return 4;
    if (run_lock.test_and_set(std::memory_order_acquire)) return 2;
    RunLock lock;
    bool result_called = false;
    try {
        return run(language, projector, request_directory, context,
                   progress, result, result_called);
    } catch (const Cancelled &) {
        return 3;
    } catch (...) {
        if (!result_called) {
            result_called = true;
            publish(context, result, kError, sizeof(kError) - 1);
        }
        return 1;
    }
}
