#import "PadRawScoreProbe.h"
#include "PadPreprocessing.hpp"
#include <opencv2/core.hpp>
#include <opencv2/core/utils/logger.hpp>
#include <opencv2/dnn.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include <cmath>
#include <exception>

NSDictionary<NSString *, id> *ODAIPadRawScores(NSString *modelPath, NSData *image, NSError **error) {
    NSString *stage = @"decode";
    auto fail = [&](NSString *category = @"validation", int nativeCode = 0) -> NSDictionary * {
        if (error) *error = [NSError errorWithDomain:@"io.ondeviceai.pad.probe" code:1
            userInfo:@{@"stage": stage, @"category": category, @"nativeCode": @(nativeCode)}];
        return nil;
    };
    try {
        // Separate short-lived research process; suppress ONNX exception path logging.
        cv::utils::logging::setLogLevel(cv::utils::logging::LOG_LEVEL_SILENT);
        if (image.length == 0 || image.length > 10 * 1024 * 1024) return fail();
        cv::Mat encoded(1, (int)image.length, CV_8UC1, const_cast<void *>(image.bytes));
        cv::Mat decoded = cv::imdecode(encoded, cv::IMREAD_COLOR | cv::IMREAD_IGNORE_ORIENTATION);
        // Caller hashes exactly the approved raster before entering native code.
        if (decoded.cols != 512 || decoded.rows != 512 || decoded.type() != CV_8UC3) return fail();
        stage = @"preprocessing";
        cv::Mat resized;
        cv::resize(decoded, resized, cv::Size(128, 128), 0, 0, cv::INTER_CUBIC);
        int dimensions[] = {1, 3, 128, 128};
        cv::Mat input(4, dimensions, CV_32F);
        pad_probe::rgbNchw(resized.ptr<unsigned char>(), resized.step, input.ptr<float>());
        stage = @"modelLoad";
        cv::dnn::Net network = cv::dnn::readNetFromONNX(modelPath.fileSystemRepresentation);
        if (network.empty()) return fail();
        network.setPreferableBackend(cv::dnn::DNN_BACKEND_OPENCV);
        network.setPreferableTarget(cv::dnn::DNN_TARGET_CPU);
        auto outputs = network.getUnconnectedOutLayersNames();
        if (outputs.size() != 1 || outputs[0] != "output1") return fail();
        const auto layer = network.getLayer(network.getLayerId(outputs[0]));
        NSMutableArray *runs = [NSMutableArray array];
        for (int run = 0; run < 2; ++run) {
            stage = @"inference";
            const int64 start = cv::getTickCount();
            network.setInput(input, "actual_input_1");
            cv::Mat scores = network.forward("output1");
            double millis = (cv::getTickCount() - start) * 1000.0 / cv::getTickFrequency();
            stage = @"outputValidation";
            if (scores.dims != 2 || scores.rows != 1 || scores.cols != 2 || scores.type() != CV_32FC1) return fail();
            float first = scores.at<float>(0, 0), second = scores.at<float>(0, 1);
            if (!std::isfinite(first) || !std::isfinite(second)) return fail();
            [runs addObject:@{@"scores": @[@(first), @(second)], @"inferenceMillis": @(millis)}];
        }
        return @{@"runtimeVersion": @CV_VERSION, @"inputShape": @[@1, @3, @128, @128],
            @"outputShape": @[@1, @2], @"outputLayerType": [NSString stringWithUTF8String:layer->type.c_str()],
            @"preprocessingRevision": @"whole-frame-cubic128-rgb-nchw-v1", @"runs": runs};
    } catch (const cv::Exception &exception) {
        return fail(@"opencv", exception.code);
    } catch (const std::exception &) {
        return fail(@"standard");
    } catch (...) {
        return fail(@"unknown");
    }
}
