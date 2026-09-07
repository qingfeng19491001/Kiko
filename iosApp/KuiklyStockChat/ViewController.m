//
//  ViewController.m
//  KuiklyStockChat
//
//  默认入口页面（Native UI），按钮点击后跳入 Kuikly 渲染的 `StockChat` 业务页面
//

#import "ViewController.h"
#import "KuiklyRenderViewController.h"

@interface ViewController ()

@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];
    self.title = @"StockChat";

    // 默认 pageName（与 shared 模块 StockChatPage 的 @Page(...) 注解保持一致）
    NSString *pageName = @"StockChat";

    // 顶部品牌
    UILabel *title = [[UILabel alloc] init];
    title.text = @"Kuikly StockChat";
    title.font = [UIFont boldSystemFontOfSize:28];
    title.textColor = [UIColor labelColor];
    title.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:title];

    UILabel *subtitle = [[UILabel alloc] init];
    subtitle.text = @"AI 智能股票问答 · 三端一致";
    subtitle.font = [UIFont systemFontOfSize:15];
    subtitle.textColor = [UIColor secondaryLabelColor];
    subtitle.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:subtitle];

    // 进入按钮
    UIButton *enterButton = [UIButton buttonWithType:UIButtonTypeSystem];
    UIButtonConfiguration *cfg = [UIButtonConfiguration filledButtonConfiguration];
    cfg.title = @"进入 StockChat";
    cfg.cornerStyle = UIButtonConfigurationCornerStyleLarge;
    cfg.baseBackgroundColor = [UIColor systemBlueColor];
    cfg.baseForegroundColor = [UIColor whiteColor];
    enterButton.configuration = cfg;
    enterButton.translatesAutoresizingMaskIntoConstraints = NO;
    [enterButton addTarget:self
                    action:@selector(enterKuikly)
          forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:enterButton];

    [NSLayoutConstraint activateConstraints:@[
        [title.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [title.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:120],

        [subtitle.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [subtitle.topAnchor constraintEqualToAnchor:title.bottomAnchor constant:12],

        [enterButton.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
        [enterButton.topAnchor constraintEqualToAnchor:subtitle.bottomAnchor constant:40],
        [enterButton.widthAnchor constraintEqualToConstant:240],
        [enterButton.heightAnchor constraintEqualToConstant:54],
    ]];
}

- (void)enterKuikly {
    KuiklyRenderViewController *vc = [[KuiklyRenderViewController alloc]
        initWithPageName:@"StockChat" pageData:nil];
    [self.navigationController pushViewController:vc animated:YES];
}

@end
