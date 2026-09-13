import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { pasteboard } from '@kit.BasicServicesKit';

/** 当前鸿蒙壳尚未声明系统分享权限，显式回传能力状态，避免 Kotlin 回调悬空。 */
export class KRContentActionModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRContentActionModule';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    if (method === 'copy') {
      const text = asText(params);
      pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text))
        .then(() => callback?.(JSON.stringify({ success: true })))
        .catch((error: Error) => callback?.(JSON.stringify({ success: false, error: error.message || '复制失败' })));
      return null;
    }
    if (method === 'share' && callback) {
      callback(JSON.stringify({ success: false, error: '鸿蒙端系统分享能力待宿主接入' }));
    }
    return null;
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
