//
//  KRRouterHandler.m
//  KuiklyStockChat
//
//  页面路由适配器：open/close 路由到 KuiklyRenderViewController
//

#import "KRRouterHandler.h"
#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/KRRouterModule.h>

@implementation KRRouterHandler

+ (void)load {
    [KRRouterModule registerRouterHandler:[self new]];
}

- (void)openPageWithName:(NSString *)pageName
                pageData:(NSDictionary *)pageData
              controller:(UIViewController *)controller {
    KuiklyRenderViewController *vc = [[KuiklyRenderViewController alloc]
        initWithPageName:pageName pageData:pageData];
    [controller.navigationController pushViewController:vc animated:YES];
}

- (void)closePage:(UIViewController *)controller {
    [controller.navigationController popViewControllerAnimated:YES];
}

@end
