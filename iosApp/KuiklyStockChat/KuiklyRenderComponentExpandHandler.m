//
//  KuiklyRenderComponentExpandHandler.m
//  KuiklyStockChat
//
//  图片加载扩展（Kuikly 通过 ComponentExpandHandler 处理 hr_setImageWithUrl）。
//  未引入第三方图片库时，回退到 UIImageView 默认加载。
//

#import "KuiklyRenderComponentExpandHandler.h"

@implementation KuiklyRenderComponentExpandHandler

+ (void)load {
    [KuiklyRenderBridge registerComponentExpandHandler:[self new]];
}

// 如果有 SDWebImage/SDWebImageSwiftUI 等第三方库，可以在此处实现：
// - (BOOL)hr_setImageWithUrl:(NSString *)loadURL
//                imageParams:(NSDictionary *)imageParams
//                   complete:(ImageCompletionBlock)completeBlock { ... }
//
// 当前工程不依赖第三方，所以返回 NO，由 Kuikly 走默认占位路径。

@end
