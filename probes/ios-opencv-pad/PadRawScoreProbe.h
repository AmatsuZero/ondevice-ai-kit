#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
// Isolated research harness only; no liveness policy/verdict/proof interface.
FOUNDATION_EXPORT NSDictionary<NSString *, id> * _Nullable ODAIPadRawScores(
    NSString *modelPath, NSData *image, NSError **error);
NS_ASSUME_NONNULL_END
