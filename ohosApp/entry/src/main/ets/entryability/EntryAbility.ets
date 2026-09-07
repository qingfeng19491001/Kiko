import AbilityConstant from '@ohos.app.ability.AbilityConstant';
import hilog from '@ohos.hilog';
import UIAbility from '@ohos.app.ability.UIAbility';
import Want from '@ohos.app.ability.Want';
import window from '@ohos.window';
import display from '@ohos.display';
import { KuiklyRenderAdapterManager } from '@kuikly-open/render';
import { LogAdapter } from '../kuikly/adapter/LogAdapter';
import { RouterAdapter } from '../kuikly/adapter/RouterAdapter';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(0x0000, 'StockChat', 'Ability onCreate');

    // 冷启动直达：--ps pageName StockDetail
    const pageName = want.parameters?.['pageName'] as string;
    if (pageName) {
      AppStorage.setOrCreate<string>('stockchat_page_name', pageName);
    }
  }

  onDestroy(): void {
    hilog.info(0x0000, 'StockChat', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    hilog.info(0x0000, 'StockChat', 'Ability onWindowStageCreate');
    const mainWindow = windowStage.getMainWindowSync();
    mainWindow.setWindowLayoutFullScreen(true);

    // 冷启动时 Kuikly 框架不会注入 statusBarHeight，这里补齐给页面
    const avoidArea = mainWindow.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
    const density = display.getDefaultDisplaySync().densityPixels;
    const statusBarHeightVp = avoidArea.topRect.height / density;
    AppStorage.setOrCreate<number>('status_bar_height', statusBarHeightVp);

    // 注册 Kuikly 适配器
    KuiklyRenderAdapterManager.krLogAdapter = new LogAdapter();
    KuiklyRenderAdapterManager.krRouterAdapter = new RouterAdapter();

    windowStage.loadContent('pages/Index', (err, data) => {
      if (err.code) {
        hilog.error(0x0000, 'StockChat', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err) ?? '');
        return;
      }
      hilog.info(0x0000, 'StockChat', 'Succeeded in loading the content');
    });
  }

  onWindowStageDestroy(): void {
    hilog.info(0x0000, 'StockChat', 'Ability onWindowStageDestroy');
  }

  onForeground(): void {
    hilog.info(0x0000, 'StockChat', 'Ability onForeground');
  }

  onBackground(): void {
    hilog.info(0x0000, 'StockChat', 'Ability onBackground');
  }
}
