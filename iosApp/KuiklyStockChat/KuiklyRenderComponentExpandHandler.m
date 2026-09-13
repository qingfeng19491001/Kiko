//
//  KuiklyRenderComponentExpandHandler.m
//  KuiklyStockChat
//
//  图片加载扩展。未实现 hr_setImageWithUrl 时，KRImageView 会 NSAssert 崩掉。
//

#import "KuiklyRenderComponentExpandHandler.h"
#import <UIKit/UIKit.h>

static NSString *const kKRAssetsPrefix = @"assets://";
static NSString *const kKRFilePrefix = @"file://";

static UIImage *KRImageFromAssetsSrc(NSString *src) {
    NSString *relative = [src substringFromIndex:kKRAssetsPrefix.length];
    NSString *extension = relative.pathExtension;
    NSString *name = relative.stringByDeletingPathExtension;
    NSBundle *bundle = [NSBundle mainBundle];
    NSURL *url = [bundle URLForResource:name withExtension:extension];
    if (!url) {
        url = [bundle URLForResource:relative.lastPathComponent.stringByDeletingPathExtension
                       withExtension:extension
                        subdirectory:relative.stringByDeletingLastPathComponent];
    }
    if (url.path.length > 0) {
        return [UIImage imageWithContentsOfFile:url.path];
    }
    return [UIImage imageNamed:relative.lastPathComponent.stringByDeletingPathExtension];
}

static UIImage *KRImageFromFileSrc(NSString *src) {
    NSString *path = [src substringFromIndex:kKRFilePrefix.length];
    if ([path hasPrefix:@"//"]) {
        path = [path substringFromIndex:1];
    }
    return [UIImage imageWithContentsOfFile:path];
}

@implementation KuiklyRenderComponentExpandHandler

+ (void)load {
    [KuiklyRenderBridge registerComponentExpandHandler:[self new]];
}

- (BOOL)hr_setImageWithUrl:(NSString *)loadURL
               imageParams:(NSDictionary *)imageParams
                  complete:(ImageCompletionBlock)completeBlock {
    if (loadURL.length == 0) {
        if (completeBlock) {
            completeBlock(nil, nil, nil);
        }
        return YES;
    }

    if ([loadURL hasPrefix:kKRAssetsPrefix]) {
        UIImage *image = KRImageFromAssetsSrc(loadURL);
        if (completeBlock) {
            NSError *error = image ? nil : [NSError errorWithDomain:@"KRImage" code:-1
                userInfo:@{NSLocalizedDescriptionKey: @"assets image not found"}];
            completeBlock(image, error, [NSURL URLWithString:loadURL]);
        }
        return YES;
    }

    if ([loadURL hasPrefix:kKRFilePrefix]) {
        UIImage *image = KRImageFromFileSrc(loadURL);
        if (completeBlock) {
            NSError *error = image ? nil : [NSError errorWithDomain:@"KRImage" code:-1
                userInfo:@{NSLocalizedDescriptionKey: @"file image not found"}];
            completeBlock(image, error, [NSURL URLWithString:loadURL]);
        }
        return YES;
    }

    NSURL *url = [NSURL URLWithString:loadURL];
    if (!url) {
        if (completeBlock) {
            completeBlock(nil, [NSError errorWithDomain:@"KRImage" code:-1
                userInfo:@{NSLocalizedDescriptionKey: @"invalid image url"}], nil);
        }
        return YES;
    }

    [[[NSURLSession sharedSession] dataTaskWithURL:url
                                 completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        UIImage *image = data.length > 0 ? [UIImage imageWithData:data] : nil;
        if (completeBlock) {
            completeBlock(image, error, [NSURL URLWithString:loadURL]);
        }
    }] resume];
    return YES;
}

@end
