import { ErrorReason, KRNativeRenderController, KRRecord, Kuikly } from '@kuikly-open/render';
import router from '@ohos.router';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import globalNativeManager from '../kuikly/MyNativeManager';

const DEFAULT_PAGE_NAME: string = 'MarketList';

@Entry
@Component
struct Index {
  kuiklyViewDelegate = new KuiklyViewDelegate();
  private kuiklyController: KRNativeRenderController | null = null;
  pageName?: string;
  pageData?: KRRecord;
  @State exception?: string = '';

  onBackPress(): boolean | void {
    return this.kuiklyController?.onBackPress();
  }

  aboutToAppear() {
    AppStorage.setOrCreate<UIContext>('context', this.getUIContext());

    const params = router.getParams() as Record<string, Object> | undefined;
    const routePageName = params?.['pageName'] as string | undefined;
    const wantPageName = AppStorage.get<string>('stockchat_page_name');
    const resolved = routePageName ?? wantPageName ?? DEFAULT_PAGE_NAME;
    this.pageName = resolved;

    const wantPageData = AppStorage.get<Record<string, Object>>('stockchat_page_data') ?? {};
    const routePageData = (params?.['pageData'] as KRRecord | undefined) ?? {};
    const basePageData: Record<string, Object> = {};
    Object.keys(wantPageData).forEach((key) => {
      basePageData[key] = wantPageData[key];
    });
    Object.keys(routePageData).forEach((key) => {
      basePageData[key] = routePageData[key];
    });
    if (resolved !== 'router') {
      const merged: Record<string, Object> = {};
      const keys = Object.keys(basePageData);
      for (let i = 0; i < keys.length; i++) {
        merged[keys[i]] = basePageData[keys[i]];
      }
      const statusBarHeight = AppStorage.get<number>('status_bar_height');
      if (statusBarHeight !== undefined && statusBarHeight > 0) {
        merged['statusBarHeight'] = statusBarHeight;
      }
      this.pageData = merged as KRRecord;
    } else {
      this.pageData = basePageData;
    }
  }

  build() {
    Stack() {
      Kuikly({
        pagerName: this.pageName ?? DEFAULT_PAGE_NAME,
        pagerData: this.pageData ?? {},
        assetsDir: getContext(this).resourceDir,
        delegate: this.kuiklyViewDelegate,
        nativeManager: globalNativeManager,
        onControllerReadyCallback: (controller) => {
          this.kuiklyController = controller;
        },
        onRenderException: (exception: Error, reason: ErrorReason) => {
          this.exception = `${exception.name}:\n${exception.message}`;
        },
      })
      if (this.exception) {
        Text(this.exception)
      }
    }
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.KEYBOARD])
  }

  onPageShow(): void {
    this.kuiklyViewDelegate.pageDidAppear();
  }

  onPageHide(): void {
    this.kuiklyViewDelegate.pageDidDisappear();
  }
}
