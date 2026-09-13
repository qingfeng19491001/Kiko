import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import { common } from '@kit.AbilityKit';
import { promptAction } from '@kit.ArkUI';
import { RouterAdapter } from '../adapter/RouterAdapter';

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'HRBridgeModule';
  static readonly CLOSE_PAGE = 'closePage';
  static readonly TOAST = 'toast';
  static readonly LOG = 'log';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.CLOSE_PAGE:
        this.closePage();
        break;
      case KRBridgeModule.TOAST:
        this.toast(params);
        break;
      case KRBridgeModule.LOG:
        this.log(params);
        break;
      default:
        break;
    }
    return null;
  }

  private closePage() {
    const context = AppStorage.get<common.UIAbilityContext>('abilityContext');
    if (context) {
      new RouterAdapter().closePage(context);
    }
  }

  private toast(params: KRAny) {
    const message = toastText(params);
    if (message.length === 0) {
      return;
    }
    promptAction.showToast({ message });
  }

  private log(params: KRAny) {
    if (params == null) {
      return;
    }
    console.log(`KuiklyRender ${JSON.stringify(params)}`);
  }
}

function toastText(params: KRAny): string {
  if (typeof params === 'string') {
    try {
      const json = JSON.parse(params) as Record<string, Object>;
      return String(json['content'] ?? json['message'] ?? json['text'] ?? params);
    } catch (_error) {
      return params;
    }
  }
  if (params && typeof params === 'object') {
    const record = params as Record<string, Object>;
    return String(record['content'] ?? record['message'] ?? record['text'] ?? '');
  }
  return '';
}
