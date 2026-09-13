import {
  IKuiklyViewDelegate,
  KRRenderModuleExportCreator,
  KRRenderViewExportCreator
} from '@kuikly-open/render';
import { KRBridgeModule } from './modules/KRBridgeModule';
import { KRContentActionModule } from './modules/KRContentActionModule';
import { KRAttachmentModule } from './modules/KRAttachmentModule';
import { KRVoiceModule } from './modules/KRVoiceModule';

/**
 * StockChat 鸿蒙端 View 委托。
 * KRKLineChart 的本地 HAR 尚未作为本工程模块接入，详情页 Full K 线先空着。
 */
export class KuiklyViewDelegate extends IKuiklyViewDelegate {
  getCustomRenderViewCreatorRegisterMap(): Map<string, KRRenderViewExportCreator> {
    return new Map();
  }

  getCustomRenderViewCreatorRegisterMapV2(): Map<string, KRRenderViewExportCreator> {
    return new Map();
  }

  getCustomRenderModuleCreatorRegisterMap(): Map<string, KRRenderModuleExportCreator> {
    const map: Map<string, KRRenderModuleExportCreator> = new Map();
    map.set(KRBridgeModule.MODULE_NAME, () => new KRBridgeModule());
    map.set(KRContentActionModule.MODULE_NAME, () => new KRContentActionModule());
    map.set(KRAttachmentModule.MODULE_NAME, () => new KRAttachmentModule());
    map.set(KRVoiceModule.MODULE_NAME, () => new KRVoiceModule());
    return map;
  }
}
