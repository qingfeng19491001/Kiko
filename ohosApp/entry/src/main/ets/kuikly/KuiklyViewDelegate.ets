import {
  IKuiklyViewDelegate,
  KRRenderModuleExportCreator,
  KRRenderViewExportCreator
} from '@kuikly-open/render';
import { KRKLineChart } from './views/KRKLineChart';
import { KRBridgeModule } from './modules/KRBridgeModule';
import { KRContentActionModule } from './modules/KRContentActionModule';
import { KRAttachmentModule } from './modules/KRAttachmentModule';
import { KRVoiceModule } from './modules/KRVoiceModule';

/**
 * StockChat 鸿蒙端 View 委托。
 * KRKLineChart 必须走 V1 注册，才能拿到影子节点上的 frame。
 */
export class KuiklyViewDelegate extends IKuiklyViewDelegate {
  getCustomRenderViewCreatorRegisterMap(): Map<string, KRRenderViewExportCreator> {
    const map: Map<string, KRRenderViewExportCreator> = new Map();
    map.set(KRKLineChart.VIEW_NAME, () => new KRKLineChart());
    return map;
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
