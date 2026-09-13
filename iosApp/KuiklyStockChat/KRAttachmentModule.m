#import "KRAttachmentModule.h"
#import <AVFoundation/AVFoundation.h>
#import <Photos/Photos.h>
#import <PhotosUI/PhotosUI.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#import <UIKit/UIKit.h>

static NSDictionary *KRSaveImage(UIImage *image, NSString *displayName, NSString *source);
static NSDictionary *KRCopyFile(NSURL *url, NSString *source);

@interface KRAttachmentCoordinator : NSObject <UIImagePickerControllerDelegate, UINavigationControllerDelegate, PHPickerViewControllerDelegate, UIDocumentPickerDelegate>
@property (nonatomic, copy) KuiklyRenderCallback callback;
@property (nonatomic, copy) NSString *source;
@property (nonatomic, weak) UIViewController *host;
@end

@implementation KRAttachmentCoordinator

static KRAttachmentCoordinator *KRSharedCoordinator(void) {
    static KRAttachmentCoordinator *instance;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{ instance = [KRAttachmentCoordinator new]; });
    return instance;
}

- (void)finishWithPayload:(NSDictionary *)payload {
    KuiklyRenderCallback callback = self.callback;
    self.callback = nil;
    if (callback) callback(payload);
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
    [picker dismissViewControllerAnimated:YES completion:^{
        [self finishWithPayload:@{ @"cancelled": @YES }];
    }];
}

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary<UIImagePickerControllerInfoKey,id> *)info {
    UIImage *image = info[UIImagePickerControllerOriginalImage];
    [picker dismissViewControllerAnimated:YES completion:^{
        NSDictionary *item = KRSaveImage(image, @"拍摄图片.jpg", @"CAMERA");
        NSArray *attachments = item ? @[item] : @[];
        [self finishWithPayload:@{ @"cancelled": @NO, @"attachments": attachments }];
    }];
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
    [picker dismissViewControllerAnimated:YES completion:nil];
    if (results.count == 0) {
        [self finishWithPayload:@{ @"cancelled": @YES }];
        return;
    }
    dispatch_group_t group = dispatch_group_create();
    NSMutableArray *items = [NSMutableArray array];
    for (PHPickerResult *result in results) {
        dispatch_group_enter(group);
        [result.itemProvider loadObjectOfClass:UIImage.class completionHandler:^(id<NSItemProviderReading> object, NSError *error) {
            UIImage *image = (UIImage *)object;
            NSDictionary *item = KRSaveImage(image, result.itemProvider.suggestedName ?: @"相册图片.jpg", @"PHOTO_LIBRARY");
            if (item) {
                @synchronized (items) { [items addObject:item]; }
            }
            dispatch_group_leave(group);
        }];
    }
    dispatch_group_notify(group, dispatch_get_main_queue(), ^{
        [self finishWithPayload:@{ @"cancelled": @NO, @"attachments": items }];
    });
}

- (void)documentPickerWasCancelled:(UIDocumentPickerViewController *)controller {
    [self finishWithPayload:@{ @"cancelled": @YES }];
}

- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
    NSMutableArray *items = [NSMutableArray array];
    for (NSURL *url in urls) {
        BOOL accessed = [url startAccessingSecurityScopedResource];
        NSDictionary *item = KRCopyFile(url, self.source ?: @"FILE");
        if (accessed) [url stopAccessingSecurityScopedResource];
        if (item) [items addObject:item];
    }
    [self finishWithPayload:@{ @"cancelled": @NO, @"attachments": items }];
}

@end

static NSString *KRAttachmentsDir(void) {
    NSString *dir = [[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject]
                     stringByAppendingPathComponent:@"attachments"];
    [[NSFileManager defaultManager] createDirectoryAtPath:dir withIntermediateDirectories:YES attributes:nil error:nil];
    return dir;
}

static NSString *KRMimeForName(NSString *name) {
    NSString *ext = name.pathExtension.lowercaseString;
    if ([ext isEqualToString:@"png"]) return @"image/png";
    if ([ext isEqualToString:@"jpg"] || [ext isEqualToString:@"jpeg"]) return @"image/jpeg";
    if ([ext isEqualToString:@"gif"]) return @"image/gif";
    if ([ext isEqualToString:@"webp"]) return @"image/webp";
    if ([ext isEqualToString:@"txt"] || [ext isEqualToString:@"md"] || [ext isEqualToString:@"csv"]) return @"text/plain";
    if ([ext isEqualToString:@"json"]) return @"application/json";
    if ([ext isEqualToString:@"pdf"]) return @"application/pdf";
    if ([ext isEqualToString:@"doc"]) return @"application/msword";
    if ([ext isEqualToString:@"docx"]) return @"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    return @"application/octet-stream";
}

static NSString *KRWriteThumbnail(UIImage *image, NSString *basePath) {
    CGFloat maxSide = 256;
    CGFloat scale = MIN(1, maxSide / MAX(image.size.width, image.size.height));
    CGSize size = CGSizeMake(image.size.width * scale, image.size.height * scale);
    UIGraphicsBeginImageContextWithOptions(size, YES, 1);
    [image drawInRect:CGRectMake(0, 0, size.width, size.height)];
    UIImage *thumb = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    NSString *path = [[basePath stringByDeletingPathExtension] stringByAppendingString:@"-thumb.jpg"];
    [UIImageJPEGRepresentation(thumb, 0.8) writeToFile:path atomically:YES];
    return path;
}

static NSDictionary *KRMetadata(NSString *path, NSString *mime, NSString *displayName, NSString *source, NSString *thumb) {
    NSMutableDictionary *json = [@{
        @"id": path.lastPathComponent.stringByDeletingPathExtension,
        @"displayName": displayName ?: path.lastPathComponent,
        @"mimeType": mime,
        @"byteSize": @([[[NSFileManager defaultManager] attributesOfItemAtPath:path error:nil] fileSize]),
        @"localPath": path,
        @"source": source,
        @"kind": [mime hasPrefix:@"image/"] ? @"IMAGE" : @"DOCUMENT",
        @"status": @"READY",
    } mutableCopy];
    if (thumb.length) json[@"thumbnailPath"] = thumb;
    return json;
}

static NSDictionary *KRSaveImage(UIImage *image, NSString *displayName, NSString *source) {
    if (!image) return nil;
    NSString *name = [NSString stringWithFormat:@"%@-%@", NSUUID.UUID.UUIDString, displayName ?: @"image.jpg"];
    NSString *path = [KRAttachmentsDir() stringByAppendingPathComponent:name];
    NSData *data = UIImageJPEGRepresentation(image, 0.9) ?: UIImagePNGRepresentation(image);
    if (![data writeToFile:path atomically:YES]) return nil;
    NSString *thumb = KRWriteThumbnail(image, path);
    return KRMetadata(path, @"image/jpeg", displayName, source, thumb);
}

static NSDictionary *KRCopyFile(NSURL *url, NSString *source) {
    NSString *displayName = url.lastPathComponent ?: @"附件";
    NSString *path = [KRAttachmentsDir() stringByAppendingPathComponent:[NSString stringWithFormat:@"%@-%@", NSUUID.UUID.UUIDString, displayName]];
    NSError *error = nil;
    [[NSFileManager defaultManager] copyItemAtURL:url toURL:[NSURL fileURLWithPath:path] error:&error];
    if (error) return nil;
    NSString *mime = KRMimeForName(displayName);
    NSString *thumb = nil;
    if ([mime hasPrefix:@"image/"]) {
        UIImage *image = [UIImage imageWithContentsOfFile:path];
        if (image) thumb = KRWriteThumbnail(image, path);
    }
    return KRMetadata(path, mime, displayName, [mime hasPrefix:@"image/"] && [source isEqualToString:@"FILE"] ? source : source, thumb);
}

@implementation KRAttachmentModule

- (void)open:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *source = params[@"source"] ?: @"file";
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *host = self.hr_rootView.kr_viewController;
        if (!host) {
            if (callback) callback(@{ @"cancelled": @NO, @"error": @"当前页面无法选择附件" });
            return;
        }
        KRAttachmentCoordinator *coordinator = KRSharedCoordinator();
        coordinator.callback = callback;
        coordinator.host = host;
        coordinator.source = [source isEqualToString:@"photo_library"] ? @"PHOTO_LIBRARY" : ([source isEqualToString:@"camera"] ? @"CAMERA" : @"FILE");
        if ([source isEqualToString:@"camera"]) {
            [self openCamera:host coordinator:coordinator];
        } else if ([source isEqualToString:@"photo_library"]) {
            PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
            config.filter = [PHPickerFilter imagesFilter];
            config.selectionLimit = 9;
            PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:config];
            picker.delegate = coordinator;
            [host presentViewController:picker animated:YES completion:nil];
        } else {
            UIDocumentPickerViewController *picker = [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:@[UTTypeItem] asCopy:YES];
            picker.allowsMultipleSelection = YES;
            picker.delegate = coordinator;
            [host presentViewController:picker animated:YES completion:nil];
        }
    });
}

- (void)openCamera:(UIViewController *)host coordinator:(KRAttachmentCoordinator *)coordinator {
    if (![UIImagePickerController isSourceTypeAvailable:UIImagePickerControllerSourceTypeCamera]) {
        [coordinator finishWithPayload:@{ @"cancelled": @NO, @"error": @"当前设备没有相机" }];
        return;
    }
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (!granted) {
                [coordinator finishWithPayload:@{ @"cancelled": @NO, @"error": @"未获得相机权限" }];
                return;
            }
            UIImagePickerController *picker = [UIImagePickerController new];
            picker.sourceType = UIImagePickerControllerSourceTypeCamera;
            picker.delegate = coordinator;
            [host presentViewController:picker animated:YES completion:nil];
        });
    }];
}

- (void)readFile:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *path = params[@"path"] ?: @"";
    NSFileManager *fm = [NSFileManager defaultManager];
    if (![fm fileExistsAtPath:path]) {
        if (callback) callback(@{ @"success": @NO, @"error": @"文件不存在" });
        return;
    }
    unsigned long long size = [[fm attributesOfItemAtPath:path error:nil] fileSize];
    if (size > 12ull * 1024 * 1024) {
        if (callback) callback(@{ @"success": @NO, @"error": @"附件过大，请换一张较小的图片" });
        return;
    }
    NSString *mime = KRMimeForName(path);
    if ([mime hasPrefix:@"image/"]) {
        NSString *base64 = [[NSData dataWithContentsOfFile:path] base64EncodedStringWithOptions:0];
        if (callback) callback(@{ @"success": @YES, @"dataUrl": [NSString stringWithFormat:@"data:%@;base64,%@", mime, base64 ?: @""] });
        return;
    }
    if ([mime hasPrefix:@"text/"] || [mime isEqualToString:@"application/json"]) {
        NSString *text = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil] ?: @"";
        if (callback) callback(@{ @"success": @YES, @"text": text });
        return;
    }
    if (callback) callback(@{ @"success": @YES });
}

- (void)deleteFile:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    BOOL ok = [[NSFileManager defaultManager] removeItemAtPath:params[@"path"] ?: @"" error:nil];
    if (callback) callback(@{ @"success": @(ok) });
}

- (void)fileExists:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    BOOL exists = [[NSFileManager defaultManager] fileExistsAtPath:params[@"path"] ?: @""];
    if (callback) callback(@{ @"exists": @(exists) });
}

- (void)upload:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSString *path = params[@"path"] ?: @"";
        NSData *fileData = [NSData dataWithContentsOfFile:path];
        if (!fileData) {
            if (callback) callback(@{ @"success": @NO, @"error": @"文件不存在" });
            return;
        }
        NSString *boundary = [NSString stringWithFormat:@"----StockChat%@", NSUUID.UUID.UUIDString];
        NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:params[@"url"] ?: @""]];
        request.HTTPMethod = @"POST";
        [request setValue:[NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary] forHTTPHeaderField:@"Content-Type"];
        NSDictionary *headers = params[@"headers"];
        if ([headers isKindOfClass:NSDictionary.class]) {
            [headers enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
                [request setValue:[obj description] forHTTPHeaderField:[key description]];
            }];
        }
        NSMutableData *body = [NSMutableData data];
        NSDictionary *fields = params[@"fields"];
        if ([fields isKindOfClass:NSDictionary.class]) {
            [fields enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
                [body appendData:[[NSString stringWithFormat:@"--%@\r\nContent-Disposition: form-data; name=\"%@\"\r\n\r\n%@\r\n", boundary, key, obj] dataUsingEncoding:NSUTF8StringEncoding]];
            }];
        }
        NSString *filename = path.lastPathComponent;
        [body appendData:[[NSString stringWithFormat:@"--%@\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%@\"\r\nContent-Type: application/octet-stream\r\n\r\n", boundary, filename] dataUsingEncoding:NSUTF8StringEncoding]];
        [body appendData:fileData];
        [body appendData:[[NSString stringWithFormat:@"\r\n--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
        request.HTTPBody = body;
        [[[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
            NSHTTPURLResponse *http = (NSHTTPURLResponse *)response;
            NSString *text = data ? [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] : @"";
            BOOL ok = error == nil && http.statusCode >= 200 && http.statusCode < 300;
            if (callback) callback(@{
                @"success": @(ok),
                @"body": text ?: @"",
                @"error": ok ? @"" : (error.localizedDescription ?: text ?: @"上传失败"),
            });
        }] resume];
    });
}

- (void)deleteRemote:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:params[@"url"] ?: @""]];
    request.HTTPMethod = @"DELETE";
    NSDictionary *headers = params[@"headers"];
    if ([headers isKindOfClass:NSDictionary.class]) {
        [headers enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
            [request setValue:[obj description] forHTTPHeaderField:[key description]];
        }];
    }
    [[[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        NSHTTPURLResponse *http = (NSHTTPURLResponse *)response;
        BOOL ok = error == nil && http.statusCode >= 200 && http.statusCode < 300;
        if (callback) callback(@{ @"success": @(ok) });
    }] resume];
}

@end
