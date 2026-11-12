#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface ImpressTypesetPlan : NSObject
+ (NSArray *)messagesForRequirement:(NSString *)requirement slide:(NSDictionary *)slide;
+ (nullable NSDictionary *)parseResponse:(NSString *)response slide:(NSDictionary *)slide error:(NSError **)error;
+ (NSString *)summary:(NSDictionary *)plan slide:(NSDictionary *)slide;
+ (NSDictionary *)transform:(NSDictionary *)plan part:(NSInteger)part requestId:(NSString *)requestId;
+ (NSDictionary *)contentSnapshot:(NSDictionary *)structure;
@end

NS_ASSUME_NONNULL_END
