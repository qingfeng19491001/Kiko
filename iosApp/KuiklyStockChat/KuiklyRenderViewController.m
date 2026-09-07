//
//  KuiklyRenderViewController.m
//  KuiklyStockChat
//
//  Kuikly iOS 渲染容器，基于 KuiklyRenderViewControllerDelegator。
//  ⚠️ 必须使用 Delegator（而非 BaseDelegator），否则功能异常。
//

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/KuiklyRenderViewControllerBaseDelegator.h>

@interface KuiklyRenderViewController () <KuiklyRenderViewControllerBaseDelegatorDelegate, UIGestureRecognizerDelegate>

@property (nonatomic, copy) NSString *pageName;
@property (nonatomic, strong, nullable) NSDictionary *pageData;
@property (nonatomic, strong) KuiklyRenderViewControllerBaseDelegator *delegator;

@end

@implementation KuiklyRenderViewController

- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData {
    self = [super init];
    if (self) {
        _pageName = [pageName copy];
        _pageData = pageData;
        _delegator = [[KuiklyRenderViewControllerBaseDelegator alloc] initWithPageName:pageName pageData:pageData ?: @{}];
        _delegator.delegate = self;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];
    [self.delegator viewDidLoadWithView:self.view];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self.delegator viewDidLayoutSubviews];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self.delegator viewWillAppear];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self.delegator viewDidAppear];
    UIGestureRecognizer *pop = self.navigationController.interactivePopGestureRecognizer;
    pop.enabled = YES;
    pop.delegate = self;
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [self.delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [self.delegator viewDidDisappear];
}

#pragma mark - KuiklyRenderViewControllerBaseDelegatorDelegate

- (UIView *)createLoadingView {
    UIView *v = [[UIView alloc] init];
    v.backgroundColor = [UIColor whiteColor];
    return v;
}

- (UIView *)createErrorView {
    UIView *v = [[UIView alloc] init];
    v.backgroundColor = [UIColor whiteColor];
    return v;
}

// ⚠️ 必须返回 "shared"：业务代码 pod 名（shared.cocoapods 在 shared/build.gradle.kts 配）
- (void)fetchContextCodeWithPageName:(NSString *)pageName
                      resultCallback:(KuiklyContextCodeCallback)callback {
    if (callback) {
        callback(@"shared", nil);
    }
}

#pragma mark - UIGestureRecognizerDelegate

- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer {
    if (gestureRecognizer != self.navigationController.interactivePopGestureRecognizer) {
        return YES;
    }
    if (self.navigationController.viewControllers.count <= 1) {
        return NO;
    }
    // 抽屉打开时优先关闭抽屉，不 pop 页面（onBackPressed 默认同步派发）
    __block BOOL consumed = NO;
    [self.delegator onBackPressedWithCompletion:^(BOOL isConsumed) {
        consumed = isConsumed;
    }];
    return !consumed;
}

@end
