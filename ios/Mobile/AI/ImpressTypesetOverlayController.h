#import <UIKit/UIKit.h>

@class AIService;
NS_ASSUME_NONNULL_BEGIN
typedef NSDictionary * _Nullable (^ImpressTypesetContextProvider)(NSError **error);
typedef void (^ImpressTypesetApplyHandler)(NSDictionary *context, NSDictionary *transform,
                                          NSString *requestId, void (^completion)(BOOL, NSString * _Nullable));

@interface ImpressTypesetOverlayController : NSObject
- (instancetype)initWithHost:(UIViewController *)host aiService:(AIService *)service
             contextProvider:(ImpressTypesetContextProvider)provider
                applyHandler:(ImpressTypesetApplyHandler)applyHandler;
- (void)show;
- (void)dismiss;
@end
NS_ASSUME_NONNULL_END
