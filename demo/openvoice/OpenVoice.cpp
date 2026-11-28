#include <onnxruntime_cxx_api.h>
#include <algorithm>
#include <array>
#include "OpenVoice.h"
#include <cstring>
#include <random>
#include <cmath>
#include <complex>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
using Floats = std::vector<float>;
constexpr int fftSize = 1024, hop = 256, bins = fftSize / 2 + 1;
constexpr double pi = 3.14159265358979323846;
void require(bool ok, const std::string& reason) {
    if (!ok) throw std::runtime_error(reason);
}
template<typename T> std::vector<T> readFile(const std::string& path) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    require(input.is_open(), "cannot read " + path);
    auto n = static_cast<std::streamoff>(input.tellg());
    require(n > 0 && n < 64 * 1024 * 1024 && n % sizeof(T) == 0, "invalid size " + path);
    std::vector<T> data(static_cast<size_t>(n) / sizeof(T));
    input.seekg(0);
    require(bool(input.read(reinterpret_cast<char*>(data.data()), n)), "truncated " + path);
    return data;
}
using Spectrum = std::array<std::complex<double>, fftSize>;
void fft(Spectrum& x, const Spectrum& roots) {
    for (int i = 1, j = 0; i < fftSize; ++i) {
        int bit = fftSize >> 1;
        while (j & bit) { j ^= bit; bit >>= 1; }
        j ^= bit;
        if (i < j) std::swap(x[i], x[j]);
    }
    for (int width = 2; width <= fftSize; width *= 2) {
        int half = width / 2;
        for (int start = 0; start < fftSize; start += width) {
            for (int j = 0; j < half; ++j) {
                auto odd = x[start+j+half] * roots[j * (fftSize/width)];
                auto even = x[start+j];
                x[start+j] = even + odd;
                x[start+j+half] = even - odd;
            }
        }
    }
}
void makeSpectra(const std::vector<int16_t>& pcm, int frames, Floats& forEncoder, Floats& forConverter) {
    std::array<float,fftSize> window{};
    Spectrum roots{}, block{};
    for (int i = 0; i < fftSize; ++i) {
        const double angle = 2*pi*i/fftSize;
        window[i] = static_cast<float>(0.5 - 0.5 * std::cos(angle));
        roots[i] = std::polar(1.0, -angle);
    }
    const int count = static_cast<int>(pcm.size());
    for (int t = 0; t < frames; ++t) {
        for (int i = 0; i < fftSize; ++i) {
            int n = t*hop + i - (fftSize-hop)/2;
            if (n < 0) n = -n;
            if (n >= count) n = 2*count - 2 - n;
            block[i] = (static_cast<float>(pcm[n]) / 32768.f) * window[i];
        }
        fft(block,roots);
        for (int k = 0; k < bins; ++k) {
            float value = static_cast<float>(std::sqrt(std::norm(block[k])+1e-6));
            forEncoder[t*bins+k] = value;
            if (!forConverter.empty()) forConverter[k*frames+t] = value;
        }
    }
}
Ort::Value tensor(const Ort::MemoryInfo& memory, Floats& data, std::initializer_list<int64_t> dims) {
    return Ort::Value::CreateTensor<float>(memory,data.data(),data.size(),dims.begin(),dims.size());
}
void writeLE(std::ofstream& f, uint32_t n, int bytes) {
    for (int i=0;i<bytes;++i) f.put(static_cast<char>((n>>(8*i))&255));
}
void wave(const std::string& path, const float* samples, size_t n) {
    std::ofstream f(path,std::ios::binary);
    require(f.good(),"cannot create WAV");
    const auto dataBytes = static_cast<uint32_t>(2*n); // bounded by the 30 second input
    f.write("RIFF",4); writeLE(f,36+dataBytes,4); f.write("WAVEfmt ",8); writeLE(f,16,4);
    writeLE(f,1,2); writeLE(f,1,2); writeLE(f,22050,4); writeLE(f,44100,4);
    writeLE(f,2,2); writeLE(f,16,2); f.write("data",4); writeLE(f,dataBytes,4);
    std::vector<int16_t> pcm(n);
    for (size_t i=0;i<n;++i) {
        require(std::isfinite(samples[i]),"nonfinite audio");
        pcm[i] = static_cast<int16_t>(std::nearbyint(std::clamp(samples[i]*32768.f,-32768.f,32767.f)));
    }
    f.write(reinterpret_cast<const char*>(pcm.data()),pcm.size()*sizeof(int16_t));
    f.close(); require(!f.fail(),"WAV write failed");
}

std::vector<int16_t> resampleInput(const std::string& path, bool referenceMode = false) {
    const auto bytes = readFile<uint8_t>(path);
    auto u32 = [&](int i) {
        return uint32_t(bytes[i]) | (uint32_t(bytes[i+1]) << 8) |
               (uint32_t(bytes[i+2]) << 16) | (uint32_t(bytes[i+3]) << 24);
    };
    require(bytes.size() >= 46 && !std::memcmp(bytes.data(), "RIFF", 4) &&
            !std::memcmp(bytes.data()+8, "WAVEfmt ", 8) && u32(16) == 16 &&
            u32(20) == 0x10001 && u32(32) == 0x100002 &&
            !std::memcmp(bytes.data()+36, "data", 4) && u32(40) == bytes.size()-44 &&
            (bytes.size()-44)%2 == 0, "invalid canonical PCM16 WAV");
    const int rate = static_cast<int>(u32(24));
    require(rate == 16000 || rate == 44100 || rate == 48000, "unsupported sample rate");
    const int count = static_cast<int>((bytes.size()-44)/2);
    const double minSeconds = referenceMode ? 0.5 : 0.05;
    require(count >= minSeconds*rate && count <= 30*rate,
            referenceMode ? "audio must last 0.5 to 30 seconds" : "audio must last 0.05 to 30 seconds");
    std::vector<int16_t> input(count);
    std::memcpy(input.data(), bytes.data()+44, count*2);
    const double step = rate/22050.0, cutoff = std::min(1.0, 1.0/step);
    const int radius = 16;
    std::vector<int16_t> pcm(static_cast<size_t>(std::lround(count/step)));
    for (size_t i = 0; i < pcm.size(); ++i) {
        double position = i*step, sum = 0, weight = 0;
        int center = static_cast<int>(position);
        for (int j = center-radius+1; j <= center+radius; ++j) {
            double d = position-j;
            double sinc = std::abs(d) < 1e-9 ? cutoff : std::sin(pi*cutoff*d)/(pi*d);
            double w = sinc * (0.5+0.5*std::cos(pi*d/radius));
            sum += input[std::clamp(j, 0, count-1)]*w;
            weight += w;
        }
        pcm[i] = static_cast<int16_t>(std::clamp(std::lround(sum/weight), -32768L, 32767L));
    }
    return pcm;
}

void convert(const std::string& assets, const Ort::Env& env, const Ort::SessionOptions& opts,
             const Ort::MemoryInfo& memory, Ort::AllocatorWithDefaultOptions& allocator,
             Floats& spectrum, Floats& source, Floats& target, int frames,
             const std::string& output, const char* profileDirectory = nullptr) {
    Ort::Session converter(env, (assets+"/converter.onnx").c_str(), opts);
    auto name = converter.GetOutputNameAllocated(0, allocator);
    const char *in[] = {"spec","noise","src_se","tgt_se"}, *out[] = {name.get()};
    std::mt19937 generator(0);
    std::normal_distribution<float> normal(0, 1);
    Floats noise(192*frames);
    for (float& sample : noise) sample = normal(generator);
    // Bound decoder working memory. Reuse the same latent noise across overlapping windows.
    constexpr int windowFrames = 840, overlapFrames = 12;
    Floats result(frames*hop);
    for (int start = 0; start < frames; start += windowFrames-overlapFrames) {
        int n = std::min(windowFrames, frames-start);
        auto window = [&](const Floats& all, int channels) {
            Floats slice(channels*n);
            for (int i = 0; i < channels; ++i)
                std::copy_n(all.data()+i*frames+start, n, slice.data()+i*n);
            return slice;
        };
        auto spec = window(spectrum,bins), latent = window(noise,192);
        std::array<Ort::Value,4> args = {tensor(memory,spec,{1,bins,n}), tensor(memory,latent,{1,192,n}),
            tensor(memory,source,{1,256,1}), tensor(memory,target,{1,256,1})};
        auto y = converter.Run(Ort::RunOptions{nullptr}, in, args.data(), args.size(), out, 1);
        require(y[0].GetTensorTypeAndShapeInfo().GetShape() == std::vector<int64_t>({1,1,n*hop}), "decoder shape");
        const float *data = y[0].GetTensorData<float>();
        for (int i = 0; i < n*hop; ++i) {
            float mix = start == 0 ? 1.f : std::min(1.f, float(i)/(overlapFrames*hop));
            auto& sample = result[start*hop+i];
            sample = (1.f-mix)*sample + mix*data[i];
        }
        if (start+n == frames) break;
    }
    if (profileDirectory) converter.EndProfilingAllocated(allocator);
    wave(output, result.data(), result.size());
}

void renderReference(const std::string& assets, const std::string& reference,
                     const std::string& input, const std::string& output) {
    const auto refPcm = resampleInput(reference, true);
    const auto inPcm = resampleInput(input, true);
    const int frames = static_cast<int>(inPcm.size())/hop;
    const int refFrames = static_cast<int>(refPcm.size())/hop;
    require(frames > 0 && refFrames > 0, "audio shorter than one frame");
    Floats transposed(frames*bins), spectrum(frames*bins);
    makeSpectra(inPcm, frames, transposed, spectrum);
    Floats refTransposed(refFrames*bins), converterSpectrum;
    makeSpectra(refPcm, refFrames, refTransposed, converterSpectrum); // reference STFT only feeds the encoder
    Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "openvoice-demo");
    Ort::SessionOptions opts;
    opts.SetIntraOpNumThreads(2);
    opts.SetInterOpNumThreads(1);
    auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    Ort::AllocatorWithDefaultOptions allocator;
    Floats source, target;
    {
        // A single encoder session serves both embeddings and is released before the converter.
        Ort::Session encoder(env, (assets+"/ref_encoder.onnx").c_str(), opts);
        auto name = encoder.GetOutputNameAllocated(0, allocator);
        const char *in[] = {"spec"}, *out[] = {name.get()};
        auto inSpec = tensor(memory, transposed, {1,frames,bins});
        auto inEmbedding = encoder.Run(Ort::RunOptions{nullptr}, in, &inSpec, 1, out, 1);
        require(inEmbedding[0].GetTensorTypeAndShapeInfo().GetShape() == std::vector<int64_t>({1,256}), "encoder shape");
        const float *sourceData = inEmbedding[0].GetTensorData<float>();
        source.assign(sourceData, sourceData+256);
        auto refSpec = tensor(memory, refTransposed, {1,refFrames,bins});
        auto refEmbedding = encoder.Run(Ort::RunOptions{nullptr}, in, &refSpec, 1, out, 1);
        require(refEmbedding[0].GetTensorTypeAndShapeInfo().GetShape() == std::vector<int64_t>({1,256}), "encoder shape");
        const float *targetData = refEmbedding[0].GetTensorData<float>();
        target.assign(targetData, targetData+256);
        for (float value : target) require(std::isfinite(value), "nonfinite voice embedding");
    }
    convert(assets, env, opts, memory, allocator, spectrum, source, target, frames, output);
}

void render(const std::string& assets, const std::string& input, const std::string& voice,
            const std::string& output, const char *profileDirectory = nullptr) {
    require(voice == "voiceA" || voice == "voiceB" || voice == "voiceC", "unknown voice");
    const auto pcm = resampleInput(input);
    int frames = static_cast<int>(pcm.size())/hop;
    Floats transposed(frames*bins), spectrum(frames*bins);
    makeSpectra(pcm, frames, transposed, spectrum);
    Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "openvoice-demo");
    Ort::SessionOptions opts;
    opts.SetIntraOpNumThreads(2);
    opts.SetInterOpNumThreads(1);
    auto encoderOptions = opts.Clone();
#ifdef __APPLE__
    if (profileDirectory) {
        encoderOptions.AppendExecutionProvider("CoreML", {{"ModelFormat", "MLProgram"}});
        encoderOptions.EnableProfiling((std::string(profileDirectory) + "/encoder").c_str());
        // Dynamic decoder partitions exceed the iPhone app's memory limit during Core ML compilation.
        opts.AppendExecutionProvider("CoreML", {{"ModelFormat", "MLProgram"}, {"RequireStaticInputShapes", "1"}});
        opts.EnableProfiling((std::string(profileDirectory) + "/converter").c_str());
    }
#endif
    auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    Ort::AllocatorWithDefaultOptions allocator;
    Floats source;
    {
        Ort::Session encoder(env, (assets+"/ref_encoder.onnx").c_str(), encoderOptions);
        auto name = encoder.GetOutputNameAllocated(0, allocator);
        const char *in[] = {"spec"}, *out[] = {name.get()};
        auto spec = tensor(memory, transposed, {1,frames,bins});
        auto y = encoder.Run(Ort::RunOptions{nullptr}, in, &spec, 1, out, 1);
        require(y[0].GetTensorTypeAndShapeInfo().GetShape() == std::vector<int64_t>({1,256}), "encoder shape");
        const float *data = y[0].GetTensorData<float>();
        source.assign(data,data+256);
        if (profileDirectory) encoder.EndProfilingAllocated(allocator);
    }
    auto target = readFile<float>(assets+"/"+voice+".f32");
    require(target.size() == 256, "invalid voice embedding");
    convert(assets, env, opts, memory, allocator, spectrum, source, target, frames, output, profileDirectory);
}
}
#ifdef __APPLE__
int openvoice_render_coreml(const char *assets, const char *input, const char *voice, const char *output,
                            const char *profile_directory) {
    try {
        render(assets, input, voice, output, profile_directory);
        return 0;
    } catch (const std::exception& error) {
        std::fprintf(stderr, "OpenVoice Core ML: %s\n", error.what());
        return 1;
    }
}
#endif

int openvoice_render(const char *assets, const char *input, const char *voice, const char *output) {
    try {
        render(assets,input,voice,output);
        return 0;
    } catch (const std::exception& error) {
        std::fprintf(stderr,"OpenVoice: %s\n", error.what());
        return 1;
    }
}

int openvoice_render_reference(const char *assets, const char *reference, const char *input, const char *output) {
    try {
        renderReference(assets, reference, input, output);
        return 0;
    } catch (const std::exception& error) {
        std::fprintf(stderr, "OpenVoice reference: %s\n", error.what());
        return 1;
    }
}
