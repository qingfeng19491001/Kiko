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

    KuiklyRenderViewController *root = [[KuiklyRenderViewController alloc]
        initWithPageName:@"MarketList" pageData:nil];
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:root];
    nav.navigationBarHidden = YES;

    self.window.rootViewController = nav;
    [self.window makeKeyAndVisible];
    return YES;
}

@end
