#import "ODAIOpenCVNative.h"
#include <opencv2/core.hpp>
#include <opencv2/core/utils/logger.hpp>
#include <opencv2/dnn.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/objdetect/face.hpp>
#include <algorithm>
#include <cmath>

static void fail(NSError **error, ODAIOpenCVErrorCode code, NSString *stage) {
    if (error) *error = [NSError errorWithDomain:@"io.ondeviceai.opencv.identity"
        code:code userInfo:@{ @"stage": stage }];
}

static bool usable(const cv::Mat &faces, const cv::Size &size) {
    if (faces.rows != 1 || faces.cols != 15 || faces.type() != CV_32FC1) return false;
    const float *f = faces.ptr<float>(0);
    for (int i = 0; i < 15; ++i) if (!std::isfinite(f[i])) return false;
    if (f[2] <= 0 || f[3] <= 0) return false;
    for (int i = 4; i <= 12; i += 2)
        if (f[i] < 0 || f[i] > size.width - 1 || f[i + 1] < 0 || f[i + 1] > size.height - 1) return false;
    auto distance = [f](int a, int b) {
        return (f[a] - f[b]) * (f[a] - f[b]) + (f[a + 1] - f[b + 1]) * (f[a + 1] - f[b + 1]);
    };
    return distance(4, 6) >= 1 && distance(10, 12) >= 1;
}

@implementation ODAIOpenCVResult
- (instancetype)initWithCount:(NSInteger)count usable:(BOOL)geometry feature:(NSData *)feature {
    if ((self = [super init])) { _faceCount = count; _usableGeometry = geometry; _feature = feature; }
    return self;
}
@end

@implementation ODAIOpenCVRuntime {
    cv::Ptr<cv::FaceDetectorYN> _detector;
    cv::Ptr<cv::FaceRecognizerSF> _recognizer;
}
+ (NSString *)runtimeVersion { return @CV_VERSION; }
- (instancetype)initWithDetectorPath:(NSString *)detectorPath recognizerPath:(NSString *)recognizerPath error:(NSError **)error {
    if (!(self = [super init])) return nil;
    NSString *stage = @"detectorLoad";
    try {
        // OpenCV's process-wide logger may otherwise print raw ONNX paths before throwing.
        cv::utils::logging::setLogLevel(cv::utils::logging::LOG_LEVEL_SILENT);
        _detector = cv::FaceDetectorYN::create(detectorPath.fileSystemRepresentation, "", cv::Size(640, 640),
            .9f, .3f, 5000, cv::dnn::DNN_BACKEND_OPENCV, cv::dnn::DNN_TARGET_CPU);
        stage = @"recognizerLoad";
        _recognizer = cv::FaceRecognizerSF::create(recognizerPath.fileSystemRepresentation, "",
            cv::dnn::DNN_BACKEND_OPENCV, cv::dnn::DNN_TARGET_CPU);
        if (_detector.empty() || _recognizer.empty()) { fail(error, ODAIOpenCVErrorUnavailable, stage); return nil; }
    } catch (...) { fail(error, ODAIOpenCVErrorUnavailable, stage); return nil; }
    return self;
}
- (ODAIOpenCVResult *)process:(NSData *)bytes includeFeature:(BOOL)includeFeature shouldStop:(BOOL (^)(void))stop error:(NSError **)error {
    NSString *stage = @"decode";
    auto stopped = [&]() { if (!stop()) return false; fail(error, ODAIOpenCVErrorStopped, stage); return true; };
    try {
        if (stopped()) return nil;
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) { fail(error, ODAIOpenCVErrorInvalidInput, stage); return nil; }
        // Header dimensions are checked in Swift before this allocating decode.
        cv::Mat encoded(1, (int)bytes.length, CV_8UC1, const_cast<void *>(bytes.bytes));
        cv::Mat decoded = cv::imdecode(encoded, cv::IMREAD_COLOR | cv::IMREAD_IGNORE_ORIENTATION);
        if (decoded.empty() || decoded.cols < 16 || decoded.rows < 16 || decoded.cols > 4096 || decoded.rows > 4096 ||
            (int64_t)decoded.cols * decoded.rows > 4194304) { fail(error, ODAIOpenCVErrorInvalidInput, stage); return nil; }
        if (stopped()) return nil;
        double scale = std::min(1.0, 640.0 / std::max(decoded.cols, decoded.rows));
        cv::Mat input;
        cv::resize(decoded, input, cv::Size(std::max(1, (int)std::lround(decoded.cols * scale)),
            std::max(1, (int)std::lround(decoded.rows * scale))), 0, 0, cv::INTER_LINEAR);
        stage = @"detection";
        if (stopped()) return nil;
        _detector->setInputSize(input.size());
        cv::Mat faces;
        _detector->detect(input, faces);
        if (stopped()) return nil;
        bool geometry = usable(faces, input.size());
        NSData *featureBytes = nil;
        if (includeFeature) {
            if (faces.rows > 1) { fail(error, ODAIOpenCVErrorMultipleFaces, stage); return nil; }
            if (!geometry) { fail(error, ODAIOpenCVErrorInvalidInput, stage); return nil; }
            stage = @"alignment";
            cv::Mat aligned;
            _recognizer->alignCrop(input, faces.row(0), aligned);
            if (stopped()) return nil;
            stage = @"embedding";
            cv::Mat feature;
            _recognizer->feature(aligned, feature);
            if (stopped()) return nil;
            if (feature.type() != CV_32FC1 || feature.total() != 128 || !feature.isContinuous()) {
                fail(error, ODAIOpenCVErrorInvalidInput, stage); return nil;
            }
            featureBytes = [NSData dataWithBytes:feature.ptr<float>() length:128 * sizeof(float)];
        }
        if (stopped()) return nil;
        return [[ODAIOpenCVResult alloc] initWithCount:faces.rows usable:geometry feature:featureBytes];
    } catch (...) { fail(error, ODAIOpenCVErrorUnavailable, stage); return nil; }
}
@end
