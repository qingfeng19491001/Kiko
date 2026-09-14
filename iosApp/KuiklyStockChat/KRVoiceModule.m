#import "KRVoiceModule.h"
#import <AVFAudio/AVFAudio.h>
#import <Speech/Speech.h>
#import <AVFoundation/AVFoundation.h>

@interface KRVoiceEngine : NSObject <AVSpeechSynthesizerDelegate>
@property (nonatomic, strong) AVSpeechSynthesizer *synthesizer;
@property (nonatomic, copy) KuiklyRenderCallback speakCallback;
@property (nonatomic, strong) AVAudioEngine *audioEngine;
@property (nonatomic, strong) SFSpeechRecognizer *recognizer;
@property (nonatomic, strong) SFSpeechAudioBufferRecognitionRequest *recognitionRequest;
@property (nonatomic, strong) SFSpeechRecognitionTask *recognitionTask;
@property (nonatomic, copy) NSString *latestText;
@property (nonatomic, copy) KuiklyRenderCallback finishCallback;
@property (nonatomic, assign) BOOL listening;
@end

@implementation KRVoiceEngine

static KRVoiceEngine *KRSharedVoice(void) {
    static KRVoiceEngine *engine;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{ engine = [KRVoiceEngine new]; });
    return engine;
}

- (void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didFinishSpeechUtterance:(AVSpeechUtterance *)utterance {
    if (self.speakCallback) self.speakCallback(@{ @"success": @YES });
    self.speakCallback = nil;
}

- (void)speechSynthesizer:(AVSpeechSynthesizer *)synthesizer didCancelSpeechUtterance:(AVSpeechUtterance *)utterance {
    self.speakCallback = nil;
}

- (void)stopCapture {
    if (self.audioEngine.isRunning) {
        [self.audioEngine stop];
    }
    if (self.audioEngine.inputNode) {
        [self.audioEngine.inputNode removeTapOnBus:0];
    }
    [self.recognitionRequest endAudio];
    self.recognitionRequest = nil;
    self.listening = NO;
}

- (void)cancelRecognition {
    [self.recognitionTask cancel];
    self.recognitionTask = nil;
    self.recognizer = nil;
    self.finishCallback = nil;
    self.latestText = @"";
    [self stopCapture];
}

- (void)deliverFinishWithError:(NSString *)error {
    KuiklyRenderCallback callback = self.finishCallback;
    self.finishCallback = nil;
    NSString *text = self.latestText ?: @"";
    self.recognitionTask = nil;
    self.recognitionRequest = nil;
    if (!callback) return;
    if (text.length) {
        callback(@{ @"text": text });
    } else {
        callback(@{ @"error": error ?: @"未识别到有效语音" });
    }
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
                [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus status) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        if (status != SFSpeechRecognizerAuthorizationStatusAuthorized) {
                            if (callback) callback(@{ @"success": @NO, @"message": @"请允许语音识别权限后再试" });
                            return;
                        }
                        [self beginLiveRecognition:callback];
                    });
                }];
            });
        }];
    });
}

- (void)beginLiveRecognition:(KuiklyRenderCallback)callback {
    KRVoiceEngine *engine = KRSharedVoice();
    [engine cancelRecognition];

    NSError *error = nil;
    AVAudioSession *session = AVAudioSession.sharedInstance;
    [session setCategory:AVAudioSessionCategoryPlayAndRecord
                    mode:AVAudioSessionModeMeasurement
                 options:AVAudioSessionCategoryOptionDefaultToSpeaker | AVAudioSessionCategoryOptionDuckOthers
                   error:&error];
    [session setActive:YES withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:&error];

    SFSpeechRecognizer *recognizer = [[SFSpeechRecognizer alloc] initWithLocale:[NSLocale localeWithLocaleIdentifier:@"zh-CN"]];
    if (!recognizer || !recognizer.isAvailable) {
        if (callback) callback(@{ @"success": @NO, @"message": @"当前设备暂不可用中文语音识别" });
        return;
    }

    SFSpeechAudioBufferRecognitionRequest *request = [SFSpeechAudioBufferRecognitionRequest new];
    request.shouldReportPartialResults = YES;
    if (@available(iOS 16.0, *)) {
        request.addsPunctuation = YES;
    }

    AVAudioEngine *audioEngine = [AVAudioEngine new];
    AVAudioInputNode *input = audioEngine.inputNode;
    AVAudioFormat *format = [input outputFormatForBus:0];
    if (format.sampleRate <= 0 || format.channelCount <= 0) {
        if (callback) callback(@{ @"success": @NO, @"message": @"无法启动录音" });
        return;
    }

    engine.recognizer = recognizer;
    engine.recognitionRequest = request;
    engine.audioEngine = audioEngine;
    engine.latestText = @"";
    engine.listening = YES;

    engine.recognitionTask = [recognizer recognitionTaskWithRequest:request resultHandler:^(SFSpeechRecognitionResult *result, NSError *taskError) {
        dispatch_async(dispatch_get_main_queue(), ^{
            KRVoiceEngine *shared = KRSharedVoice();
            if (result.bestTranscription.formattedString.length) {
                shared.latestText = result.bestTranscription.formattedString;
            }
            if (taskError) {
                [shared stopCapture];
                if (shared.finishCallback) {
                    [shared deliverFinishWithError:taskError.localizedDescription ?: @"语音识别失败"];
                }
                return;
            }
            if (result.isFinal && shared.finishCallback) {
                [shared deliverFinishWithError:@"未识别到有效语音"];
            }
        });
    }];

    [input installTapOnBus:0 bufferSize:1024 format:format block:^(AVAudioPCMBuffer *buffer, AVAudioTime *when) {
        [KRSharedVoice().recognitionRequest appendAudioPCMBuffer:buffer];
    }];
    [audioEngine prepare];
    if (![audioEngine startAndReturnError:&error]) {
        [engine cancelRecognition];
        if (callback) callback(@{ @"success": @NO, @"message": error.localizedDescription ?: @"无法启动录音" });
        return;
    }
    if (callback) callback(@{ @"success": @YES });
}

- (void)finishListening:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        KRVoiceEngine *engine = KRSharedVoice();
        if (!engine.listening && !engine.recognitionTask) {
            if (callback) callback(@{ @"error": @"未获取到有效录音" });
            return;
        }
        engine.finishCallback = callback;
        [engine stopCapture];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if (engine.finishCallback) {
                [engine deliverFinishWithError:@"未识别到有效语音"];
            }
        });
    });
}

- (void)cancelListening:(NSDictionary *)args {
    dispatch_async(dispatch_get_main_queue(), ^{
        [KRSharedVoice() cancelRecognition];
    });
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
