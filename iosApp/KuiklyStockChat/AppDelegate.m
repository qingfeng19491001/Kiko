//
//  AppDelegate.m
//  KuiklyStockChat
//

#import "AppDelegate.h"
#import "KuiklyRenderViewController.h"

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    self.window = [[UIWindow alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
    self.window.backgroundColor = [UIColor whiteColor];

    NSString *pageName = @"MarketList";
    NSDictionary *pageData = nil;
    NSArray<NSString *> *args = [NSProcessInfo processInfo].arguments;
    for (NSUInteger i = 0; i < args.count; i++) {
        if ([args[i] isEqualToString:@"-pageName"] && i + 1 < args.count) {
            pageName = args[i + 1];
        } else if ([args[i] isEqualToString:@"-pageData"] && i + 1 < args.count) {
            NSData *data = [args[i + 1] dataUsingEncoding:NSUTF8StringEncoding];
            if (data) {
                pageData = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
            }
        }
    }

    KuiklyRenderViewController *root = [[KuiklyRenderViewController alloc]
        initWithPageName:pageName pageData:pageData];
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:root];
    nav.navigationBarHidden = YES;

    self.window.rootViewController = nav;
    [self.window makeKeyAndVisible];
    return YES;
}

@end
