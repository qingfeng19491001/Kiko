//
//  KuiklyRenderViewController.h
//  KuiklyStockChat
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface KuiklyRenderViewController : UIViewController

- (instancetype)initWithPageName:(NSString *)pageName
                        pageData:(nullable NSDictionary *)pageData;

@end

NS_ASSUME_NONNULL_END
