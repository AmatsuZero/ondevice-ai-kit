#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Stable, sanitized native boundary. No paths or native exception text escape.
typedef NS_ENUM(NSInteger, ODAIOpenCVErrorCode) {
    ODAIOpenCVErrorInvalidInput = 1,
    ODAIOpenCVErrorMultipleFaces = 2,
    ODAIOpenCVErrorStopped = 3,
    ODAIOpenCVErrorUnavailable = 4
};

@interface ODAIOpenCVResult : NSObject
@property(nonatomic, readonly) NSInteger faceCount;
@property(nonatomic, readonly) BOOL usableGeometry;
@property(nonatomic, readonly, nullable) NSData *feature;
@end

/// Calls and destruction must be serialized by the owning Swift runtime.
@interface ODAIOpenCVRuntime : NSObject
+ (NSString *)runtimeVersion;
- (nullable instancetype)initWithDetectorPath:(NSString *)detectorPath
                              recognizerPath:(NSString *)recognizerPath
                                       error:(NSError **)error;
- (nullable ODAIOpenCVResult *)process:(NSData *)encodedImage
                       includeFeature:(BOOL)includeFeature
                           shouldStop:(BOOL (^)(void))shouldStop
                                error:(NSError **)error;
@end

NS_ASSUME_NONNULL_END
