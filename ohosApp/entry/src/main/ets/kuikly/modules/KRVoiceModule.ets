import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { common, abilityAccessCtrl, Permissions } from '@kit.AbilityKit';
import { speechRecognizer, textToSpeech } from '@kit.CoreSpeechKit';
import { audio } from '@kit.AudioKit';
import { BusinessError } from '@kit.BasicServicesKit';

export class KRVoiceModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRVoiceModule';

  private asrEngine?: speechRecognizer.SpeechRecognitionEngine;
  private ttsEngine?: textToSpeech.TextToSpeechEngine;
  private capturer?: audio.AudioCapturer;
  private sessionId: string = 'stockchat';
  private latestText: string = '';
  private startCallback: KuiklyRenderCallback | null = null;
  private finishCallback: KuiklyRenderCallback | null = null;
  private speakCallback: KuiklyRenderCallback | null = null;
  private capturing: boolean = false;
  private asrReady: boolean = false;

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    switch (method) {
      case 'startListening':
        this.startListening(callback);
        break;
      case 'finishListening':
        this.finishListening(callback);
        break;
      case 'cancelListening':
        this.cancelListening();
        break;
      case 'speak':
        this.speak(asText(params), callback);
        break;
      case 'stopSpeaking':
        this.stopSpeaking();
        break;
      default:
        break;
    }
    return null;
  }

  private abilityContext(): common.UIAbilityContext | undefined {
    return AppStorage.get<common.UIAbilityContext>('abilityContext');
  }

  private async startListening(callback: KuiklyRenderCallback | null): Promise<void> {
    const context = this.abilityContext();
    if (!context) {
      callback?.(JSON.stringify({ success: false, message: '语音模块未就绪' }));
      return;
    }
    try {
      const granted = await this.ensurePermission(context, 'ohos.permission.MICROPHONE');
      if (!granted) {
        callback?.(JSON.stringify({ success: false, message: '请允许麦克风权限后再试' }));
        return;
      }
      this.startCallback = callback;
      this.latestText = '';
      this.asrReady = false;
      await this.ensureAsr();
      this.sessionId = `stockchat${Date.now()}`;
      this.asrEngine?.startListening({
        sessionId: this.sessionId,
        audioInfo: { audioType: 'pcm', sampleRate: 16000, soundChannel: 1, sampleBit: 16 }
      });
      await this.startCapturer();
      setTimeout(() => {
        if (this.startCallback) {
          this.asrReady = true;
          const start = this.startCallback;
          this.startCallback = null;
          start(JSON.stringify({ success: true }));
        }
      }, 600);
    } catch (error) {
      const err = error as BusinessError;
      callback?.(JSON.stringify({ success: false, message: err.message ?? '无法启动录音' }));
    }
  }

  private finishListening(callback: KuiklyRenderCallback | null): void {
    this.finishCallback = callback;
    this.stopCapturer();
    try {
      this.asrEngine?.finish(this.sessionId);
    } catch (_error) {
      callback?.(JSON.stringify({ error: this.latestText ? undefined : '未获取到有效录音', text: this.latestText }));
    }
  }

  private cancelListening(): void {
    this.stopCapturer();
    try {
      this.asrEngine?.cancel(this.sessionId);
    } catch (_error) {
    }
    this.latestText = '';
    this.finishCallback = null;
  }

  private async speak(text: string, callback: KuiklyRenderCallback | null): Promise<void> {
    if (!text) {
      callback?.(JSON.stringify({ success: true }));
      return;
    }
    try {
      await this.ensureTts();
      this.speakCallback = callback;
      const speakParams: textToSpeech.SpeakParams = {
        requestId: `speak${Date.now()}`
      };
      this.ttsEngine?.speak(text.substring(0, text.length > 800 ? 800 : text.length), speakParams);
    } catch (error) {
      const err = error as BusinessError;
      callback?.(JSON.stringify({ success: false, message: err.message ?? '语音播报失败' }));
    }
  }

  private stopSpeaking(): void {
    try {
      this.ttsEngine?.stop();
    } catch (_error) {
    }
    this.speakCallback = null;
  }

  private async ensureAsr(): Promise<void> {
    if (this.asrEngine) {
      return;
    }
    this.asrEngine = await speechRecognizer.createEngine({
      language: 'zh-CN',
      online: 1,
      extraParams: { recognizerMode: 'short' }
    });
    this.asrEngine.setListener({
      onStart: (_sessionId: string, _message: string) => {
        this.asrReady = true;
        const start = this.startCallback;
        this.startCallback = null;
        start?.(JSON.stringify({ success: true }));
      },
      onEvent: (_sessionId: string, _code: number, _message: string) => {},
      onResult: (_sessionId: string, result: speechRecognizer.SpeechRecognitionResult) => {
        if (result.result) {
          this.latestText = result.result;
        }
      },
      onComplete: (_sessionId: string, _message: string) => {
        const cb = this.finishCallback;
        this.finishCallback = null;
        if (cb) {
          if (this.latestText) cb(JSON.stringify({ text: this.latestText }));
          else cb(JSON.stringify({ error: '未识别到有效语音' }));
        }
      },
      onError: (_sessionId: string, _code: number, message: string) => {
        const start = this.startCallback;
        this.startCallback = null;
        if (start) {
          start(JSON.stringify({ success: false, message: message || '语音识别失败' }));
          return;
        }
        const finish = this.finishCallback;
        this.finishCallback = null;
        finish?.(JSON.stringify({ error: message || '语音识别失败' }));
      }
    });
  }

  private async ensureTts(): Promise<void> {
    if (this.ttsEngine) {
      return;
    }
    this.ttsEngine = await textToSpeech.createEngine({
      language: 'zh-CN',
      person: 0,
      online: 1
    });
    this.ttsEngine.setListener({
      onStart: (_requestId: string, _response: textToSpeech.StartResponse) => {},
      onComplete: (_requestId: string, _response: textToSpeech.CompleteResponse) => {
        const cb = this.speakCallback;
        this.speakCallback = null;
        cb?.(JSON.stringify({ success: true }));
      },
      onStop: (_requestId: string, _response: textToSpeech.StopResponse) => {
        this.speakCallback = null;
      },
      onError: (_requestId: string, _code: number, message: string) => {
        const cb = this.speakCallback;
        this.speakCallback = null;
        cb?.(JSON.stringify({ success: false, message: message || '语音播报失败' }));
      }
    });
  }

  private async startCapturer(): Promise<void> {
    this.stopCapturer();
    this.capturer = await audio.createAudioCapturer({
      streamInfo: {
        samplingRate: audio.AudioSamplingRate.SAMPLE_RATE_16000,
        channels: audio.AudioChannel.CHANNEL_1,
        sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
        encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW
      },
      capturerInfo: {
        source: audio.SourceType.SOURCE_TYPE_MIC,
        capturerFlags: 0
      }
    });
    await this.capturer.start();
    this.capturing = true;
    this.pumpAudio();
  }

  private pumpAudio(): void {
    const capturer = this.capturer;
    const engine = this.asrEngine;
    if (!capturer || !engine || !this.capturing) {
      return;
    }
    capturer.read(4096, true).then((data: ArrayBuffer) => {
      if (data.byteLength > 0 && this.capturing && this.asrReady) {
        engine.writeAudio(this.sessionId, new Uint8Array(data));
      }
      if (this.capturing) {
        this.pumpAudio();
      }
    }).catch(() => {
      this.capturing = false;
    });
  }

  private stopCapturer(): void {
    this.capturing = false;
    this.asrReady = false;
    const capturer = this.capturer;
    this.capturer = undefined;
    capturer?.stop().then(() => capturer.release()).catch(() => {});
  }

  private async ensurePermission(context: common.UIAbilityContext, permission: Permissions): Promise<boolean> {
    const manager = abilityAccessCtrl.createAtManager();
    const status = manager.checkAccessTokenSync(context.applicationInfo.accessTokenId, permission);
    if (status === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED) {
      return true;
    }
    const result = await manager.requestPermissionsFromUser(context, [permission]);
    return result.authResults[0] === abilityAccessCtrl.GrantStatus.PERMISSION_GRANTED;
  }
}

function asText(params: KRAny): string {
  if (typeof params === 'string') {
    try {
      const json = JSON.parse(params) as Record<string, string>;
      return json['text'] ?? '';
    } catch (_error) {
      return params;
    }
  }
  if (params && typeof params === 'object') {
    return String((params as Record<string, Object>)['text'] ?? '');
  }
  return '';
}
