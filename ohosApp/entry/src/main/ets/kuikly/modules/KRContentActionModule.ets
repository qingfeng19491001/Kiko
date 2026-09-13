import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { pasteboard } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { systemShare } from '@kit.ShareKit';
import { uniformTypeDescriptor as utd } from '@kit.ArkData';
import { BusinessError } from '@kit.BasicServicesKit';

export class KRContentActionModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRContentActionModule';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    if (method === 'copy') {
      const text = asField(params, 'text');
      pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text))
        .then(() => callback?.(JSON.stringify({ success: true })))
        .catch((error: Error) => callback?.(JSON.stringify({ success: false, error: error.message || '复制失败' })));
      return null;
    }
    if (method === 'share' && callback) {
      this.share(params, callback);
    }
    return null;
  }

  private share(params: KRAny, callback: KuiklyRenderCallback): void {
    const context = AppStorage.get<common.UIAbilityContext>('abilityContext');
    if (!context) {
      callback(JSON.stringify({ success: false, error: '当前页面无法分享' }));
      return;
    }
    const title = asField(params, 'title') || 'Kiko AI 回复';
    const text = asField(params, 'text');
    try {
      const data = new systemShare.SharedData({
        utd: utd.UniformDataType.PLAIN_TEXT,
        content: text,
        title: title
      });
      const controller = new systemShare.ShareController(data);
      controller.show(context, {
        selectionMode: systemShare.SelectionMode.SINGLE
      }).then(() => {
        callback(JSON.stringify({ success: true }));
      }).catch((error: BusinessError) => {
        callback(JSON.stringify({ success: false, error: error.message || '分享失败' }));
      });
    } catch (error) {
      const err = error as BusinessError;
      callback(JSON.stringify({ success: false, error: err.message || '分享失败' }));
    }
  }
}

function asField(params: KRAny, key: string): string {
  if (typeof params === 'string') {
    try {
      const json = JSON.parse(params) as Record<string, string>;
      return json[key] ?? (key === 'text' ? params : '');
    } catch (_error) {
      return key === 'text' ? params : '';
    }
  }
  if (params && typeof params === 'object') {
    return String((params as Record<string, Object>)[key] ?? '');
  }
  return '';
}
