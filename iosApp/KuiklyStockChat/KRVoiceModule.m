#import "KRVoiceModule.h"
#import <AVFAudio/AVFAudio.h>
#import <Speech/Speech.h>
#import <AVFoundation/AVFoundation.h>

@interface KRVoiceEngine : NSObject <AVSpeechSynthesizerDelegate>
@property (nonatomic, strong) AVAudioRecorder *recorder;
@property (nonatomic, strong) NSURL *recordingURL;
@property (nonatomic, strong) AVSpeechSynthesizer *synthesizer;
@property (nonatomic, copy) KuiklyRenderCallback speakCallback;
@end

@implementation KRVoiceEngine

static KRVoiceEngine *KRSharedVoice(void) {
    static KRVoiceEngine *engine;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{ engine = [KRVoiceEngine new]; });
    return engine;
}

- (NSURL *)makeRecordingURL {
    NSString *path = [NSTemporaryDirectory() stringByAppendingPathComponent:[NSString stringWithFormat:@"stockchat-voice-%@.wav", NSUUID.UUID.UUIDString]];
    return [NSURL fileURLWithPath:path];
}

- (void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didFinishSpeechUtterance:(AVSpeechUtterance *)utterance {
    if (self.speakCallback) self.speakCallback(@{ @"success": @YES });
    self.speakCallback = nil;
}

- (void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didCancelSpeechUtterance:(AVSpeechUtterance *)utterance {
    self.speakCallback = nil;
}

@end

@implementation KRVoiceModule

- (void)startListening:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        [AVAudioSession.sharedInstance requestRecordPermission:^(BOOL granted) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (!granted) {
                    if (callback) callback(@{ @"success": @NO, @"message": @"请允许麦克风权限后再试" });
                    return;
                }
                NSError *error = nil;
                [AVAudioSession.sharedInstance setCategory:AVAudioSessionCategoryPlayAndRecord
                                               withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker
                                                     error:&error];
                [AVAudioSession.sharedInstance setActive:YES error:&error];
                KRVoiceEngine *engine = KRSharedVoice();
                [engine.recorder stop];
                NSURL *url = [engine makeRecordingURL];
                NSDictionary *settings = @{
                    AVFormatIDKey: @(kAudioFormatLinearPCM),
                    AVSampleRateKey: @16000,
                    AVNumberOfChannelsKey: @1,
                    AVLinearPCMBitDepthKey: @16,
                    AVLinearPCMIsFloatKey: @NO,
                    AVLinearPCMIsBigEndianKey: @NO,
                };
                engine.recorder = [[AVAudioRecorder alloc] initWithURL:url settings:settings error:&error];
                engine.recordingURL = url;
                if (!engine.recorder || ![engine.recorder record]) {
                    if (callback) callback(@{ @"success": @NO, @"message": error.localizedDescription ?: @"无法启动录音" });
                    return;
                }
                if (callback) callback(@{ @"success": @YES });
            });
        }];
    });
}

- (void)finishListening:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    KRVoiceEngine *engine = KRSharedVoice();
    NSURL *url = engine.recordingURL;
    [engine.recorder stop];
    engine.recorder = nil;
    engine.recordingURL = nil;
    if (!url) {
        if (callback) callback(@{ @"error": @"未获取到有效录音" });
        return;
    }
    [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus status) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (status != SFSpeechRecognizerAuthorizationStatusAuthorized) {
                if (callback) callback(@{ @"error": @"未获得语音识别权限" });
                return;
            }
            SFSpeechRecognizer *recognizer = [[SFSpeechRecognizer alloc] initWithLocale:[NSLocale localeWithLocaleIdentifier:@"zh-CN"]];
            if (!recognizer) {
                if (callback) callback(@{ @"error": @"当前设备不支持中文语音识别" });
                return;
            }
            SFSpeechURLRecognitionRequest *request = [[SFSpeechURLRecognitionRequest alloc] initWithURL:url];
            request.shouldReportPartialResults = NO;
            [recognizer recognitionTaskWithRequest:request resultHandler:^(SFSpeechRecognitionResult *result, NSError *error) {
                if (error) {
                    if (callback) callback(@{ @"error": error.localizedDescription ?: @"语音识别失败" });
                    [[NSFileManager defaultManager] removeItemAtURL:url error:nil];
                    return;
                }
                if (result.isFinal) {
                    NSString *text = result.bestTranscription.formattedString ?: @"";
                    if (callback) {
                        if (text.length) callback(@{ @"text": text });
                        else callback(@{ @"error": @"未识别到有效语音" });
                    }
                    [[NSFileManager defaultManager] removeItemAtURL:url error:nil];
                }
            }];
        });
    }];
}

- (void)cancelListening:(NSDictionary *)args {
    KRVoiceEngine *engine = KRSharedVoice();
    [engine.recorder stop];
    if (engine.recordingURL) {
        [[NSFileManager defaultManager] removeItemAtURL:engine.recordingURL error:nil];
    }
    engine.recorder = nil;
    engine.recordingURL = nil;
}

- (void)speak:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *text = params[@"text"] ?: @"";
    if (text.length == 0) {
        if (callback) callback(@{ @"success": @YES });
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        KRVoiceEngine *engine = KRSharedVoice();
        [engine.synthesizer stopSpeakingAtBoundary:AVSpeechBoundaryImmediate];
        if (!engine.synthesizer) {
            engine.synthesizer = [AVSpeechSynthesizer new];
            engine.synthesizer.delegate = engine;
        }
        engine.speakCallback = callback;
        NSUInteger limit = text.length > 800 ? 800 : text.length;
        AVSpeechUtterance *utterance = [AVSpeechUtterance speechUtteranceWithString:[text substringToIndex:limit]];
        utterance.voice = [AVSpeechSynthesisVoice voiceWithLanguage:@"zh-CN"];
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate;
        [engine.synthesizer speakUtterance:utterance];
    });
}

- (void)stopSpeaking:(NSDictionary *)args {
    KRVoiceEngine *engine = KRSharedVoice();
    [engine.synthesizer stopSpeakingAtBoundary:AVSpeechBoundaryImmediate];
    engine.speakCallback = nil;
}

@end
