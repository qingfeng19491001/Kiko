import {
  IKuiklyViewDelegate,
  KRRenderModuleExportCreator,
  KRRenderViewExportCreator
} from '@kuikly-open/render';
import { KRBridgeModule } from './modules/KRBridgeModule';

/**
 * StockChat 鸿蒙端 View 委托：
 * - 不注册 KRKLineChart（stockchat 的 KLine 在 OHOS 不做 native 落点，
 *   业务侧可用 Kuikly 自带 Canvas/绘制组件折中）
 * - 仅注册通用 Bridge Module 供 Kotlin 侧调用（closePage/toast/log）
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
    return map;
  }
}
