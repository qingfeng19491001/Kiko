import { IKRRouterAdapter, KRRecord } from '@kuikly-open/render';
import router from '@ohos.router';
import common from '@ohos.app.ability.common';

export class RouterAdapter implements IKRRouterAdapter {
  openPage(context: common.UIAbilityContext, pageName: string, pageData: KRRecord): void {
    router.pushUrl({
      url: 'pages/Index',
      params: {
        pageName,
        pageData
      }
    })
  }

  closePage(context: common.UIAbilityContext): void {
    if (Number(router.getLength()) > 1) {
      router.back()
      return
    }
    // 冷启动直达页面时 back 无效，替换为 StockChat 入口
    AppStorage.setOrCreate<string>('stockchat_page_name', 'StockChat')
    router.replaceUrl({
      url: 'pages/Index',
      params: {
        pageName: 'StockChat',
        pageData: {}
      }
    })
  }
}
