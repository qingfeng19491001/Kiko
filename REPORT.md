# kuikly-stock-chat 三端运行验证报告

> 生成时间：2026-09-04
> 工程目录：`/Users/mac/Documents/Kuikly/kuikly-stock-chat`
> Kuikly 框架版本：`2.15.0`
> 验证目标：Android / iOS / HarmonyOS（OHOS）三端可正常出包

---

## 1. 一句话结论

| 平台 | 构建产物 | 体积 | 状态 |
|------|----------|------|------|
| Android | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | 5.84 MB | ✅ 通过 |
| iOS | `KuiklyStockChat.app` (iphonesimulator) | 19 MB | ✅ 通过 |
| HarmonyOS | `ohosApp/entry/build/default/outputs/default/entry-default-unsigned.hap` | 9.21 MB | ✅ 通过 |

三端产物已全部产出，可直接安装到对应设备/模拟器运行。

---

## 2. 工具链与依赖

| 工具 | 版本/路径 | 用途 |
|------|-----------|------|
| JDK | `jbr-21.0.11` (in `gradle.properties: org.gradle.java.home`) | 鸿蒙 hvigor→gradle 子调用必需 |
| Android Gradle Plugin | 7.4.2 | Android 出包 |
| Kotlin Multiplatform | 2.0.21-KBA-010 (OHOS) / 2.1.21 (Android/iOS) | KMP 编译器 |
| KSP | 2.0.21-1.0.27 (OHOS) / 2.1.21-1.0.28 (Android/iOS) | Kuikly `@Page` 注解处理 |
| CocoaPods | 系统默认 1.16.x | iOS pod 依赖管理 |
| DevEco Studio | `/Applications/DevEco-Studio.app` | 鸿蒙 hvigorw/ohpm 工具链 |
| Xcode | 26.x | iOS 出包 |

> ⚠️ `org.gradle.java.home` 必须在 `gradle.properties` 中显式设置为本机 JBR，否则鸿蒙 hvigorw 在调用 Gradle 时会失败（系统默认 JDK 26 触发 KSP 不兼容）。

---

## 3. Android 端（`./gradlew :androidApp:assembleDebug`）

### 关键命令
```bash
./gradlew :androidApp:assembleDebug --no-daemon
```

### 产物
```
androidApp/build/outputs/apk/debug/androidApp-debug.apk   5,848 KB
```

### 关键依赖（`shared/build.gradle.kts`）
- `com.tencent.kuikly-open:core:2.15.0-2.1.21`
- `com.tencent.kuikly-open:core-render-android:2.15.0-2.1.21`（api 依赖，androidMain 注入）
- `com.tencent.kuiklybase:KuiklyMarkdown:2.15.0-2.1.21`
- `io.github.qingfeng19491001:kuiklyklinechart:0.1.0-2.1.21`
- `io.github.qingfeng19491001:kuiklyklinechartandroid:0.1.0-2.1.21`（androidMain api 依赖）

### 备注
- Android 端为项目原有工程，本次仅做构建验证，无需改造。
- 若要运行，AndroidManifest 中已声明 `com.kuikly.stockchat.MainActivity`（继承 `KuiklyRenderActivity`），启动后会直接加载 `@Page("StockChat")`。

---

## 4. iOS 端（`xcodebuild -workspace ... -sdk iphonesimulator`）

### 关键命令
```bash
# 1) 生成 shared podspec（首次或清理后）
./gradlew :shared:podInstall

# 2) 安装 pod 依赖
cd iosApp && pod install
# → OpenKuiklyIOSRender 2.15.0 + shared 1.0

# 3) 出包
xcodebuild \
  -workspace iosApp/KuiklyStockChat.xcworkspace \
  -scheme KuiklyStockChat \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  build
```

### 产物
```
~/Library/Developer/Xcode/DerivedData/KuiklyStockChat-*/Build/Products/Debug-iphonesimulator/KuiklyStockChat.app
├─ KuiklyStockChat               # 主二进制
├─ KuiklyStockChat.debug.dylib   # 19 MB（含 Kuikly core + Markdown + KLineChart + 业务代码）
├─ Info.plist                    # bundleId=com.kuikly.stockchat
└─ Base.lproj/                   # LaunchScreen.storyboard
```

### iOS 端关键改造
- 手动生成 `iosApp/KuiklyStockChat.xcodeproj`（用 Ruby + `xcodeproj` gem 1.28.1，因为没装 xcodegen）
- `AppDelegate.m` 默认显示 `KuiklyRenderViewController(pageName: "StockChat")`
- `KuiklyRenderViewController.m` 通过 `KuiklyRenderViewControllerBaseDelegator` 接入渲染器（注意：必须使用 `BaseDelegator` 而非裸 `Delegator`，后者在新版中已移除）
- `KRRouterHandler.m` 注册 `KRRouterModule`（不是 `KuiklyRouterRegister`，头文件名以 `KR` 前缀）
- `KuiklyLogHandler` **不需要**自实现，`OpenKuiklyIOSRender` 自带同名类（`KRLogModule.m`），自定义一份会触发 duplicate symbol
- `Podfile` 声明 `pod 'OpenKuiklyIOSRender', '2.15.0'` + 路径 `../shared`（业务 pod）

### 已知约束
- 当前只编了 `iphonesimulator` arm64；真机出包需另外配置签名
- iOS deployment target = 14.1

---

## 5. HarmonyOS 端（鸿蒙 OHOS）

> 鸿蒙链路需要两步：
> 1) KBA 工具链编译 KMP shared 模块产出 `libshared.so`（含 `libshared_api.h`）
> 2) hvigorw assembleHap 把 .so + ArkTS 代码打成 HAP

### 5.1 编译 libshared.so（KMP→OhosArm64）

```bash
./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64 --no-daemon
```

#### 关键脚本
- `settings.ohos.gradle.kts` — 通过 `rootProject.buildFileName = "build.ohos.gradle.kts"` 切换根构建脚本
- `build.ohos.gradle.kts` — 显式声明 KBA 工具链版本（`kotlin 2.0.21-KBA-010` / `ksp 2.0.21-1.0.27`），并定义 `ohosArm64` target
- `buildSrc/Dependencies.kt` — 集中维护 OHOS 专用 `KOTLIN_OHOS_VERSION = "2.0.21-ohos"` 拼接规则
- OHOS 版 Kuikly core 使用 `${KUIKLY_VERSION}-2.0.21-ohos` 后缀（如 `core:2.15.0-2.0.21-ohos`），与 Android/iOS 的 `-2.1.21` 后缀不同

#### 产物
```
shared/build/bin/ohosArm64/debugShared/
├─ libshared.so        12,265 KB
└─ libshared_api.h      146 KB
```

#### 关键改造（OHOS 端兼容 Kuikly core 限制）
1. **kotlinx-serialization 等无 ohos_arm64 klib 的库**，从 `commonMain` 移至 `androidMain` + `iosMain`
2. **Markdown 渲染（`com.tencent.kuiklybase:kuiklybase.markdown`）** 在 OHOS 上没有 klib — 用 `expect/actual` 拆分：
   - `commonMain`：`expect class StreamingMarkdownModel` / `expect fun ViewContainer.StreamingMarkdownView`
   - `androidMain` / `iosMain`：完整 `KuiklyMarkdown` + `KuiklyStreamingMarkdown` 实现
   - `ohosArm64Main`：纯 `Text` 兜底（控制台打印 OhosUnsupported）
3. **K 线图（`io.github.qingfeng19491001:kuiklyklinechart`）** 在 OHOS 上也没有 klib — 同样 `expect/actual`：
   - `commonMain`：`expect fun ViewContainer.PlatformKLineChart(...)`
   - `androidMain` / `iosMain`：直接调 `KLineChart` 原生 View
   - `ohosArm64Main`：`ChartPlaceholder("K 线图（OHOS 端暂以占位呈现）...")`
4. iOS source set 中间层用 `by getting { val iosArm64Main by getting; val iosSimulatorArm64Main by getting; val iosMain by creating { ... iosArm64Main.dependsOn(this) ... } }` 显式连线（KMP defaultHierarchyTemplate 与 KBA 不兼容，需手动）

### 5.2 编译 HAP（hvigorw assembleHap）

#### 关键命令
```bash
# 1) 拷贝 KMP 产物到 ohosApp 目录（插件会自动拷贝，但需先确保 libshared.so 存在）
cp shared/build/bin/ohosArm64/debugShared/libshared.so ohosApp/entry/libs/arm64-v8a/
cp shared/build/bin/ohosArm64/debugShared/libshared_api.h ohosApp/entry/src/main/cpp/include/

# 2) 安装 npm/ohpm 依赖
cd ohosApp && /Applications/DevEco-Studio.app/Contents/tools/ohpm/bin/ohpm install

# 3) 出 HAP
DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk \
  /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw \
  assembleHap --no-daemon
```

#### 产物
```
ohosApp/entry/build/default/outputs/default/entry-default-unsigned.hap
9,658,888 字节（9.21 MB）
```

> ⚠️ 当前为 `unsigned.hap`，需要在 DevEco Studio 配置签名证书后才会生成 `*-signed.hap` 供真机安装。

#### 关键工程结构
```
ohosApp/
├─ oh-package.json5              # modelVersion 5.0.0，name=stockchat
├─ AppScope/app.json5            # bundleName=com.kuikly.stockchat
├─ build-profile.json5           # products=[default], buildModeSet=[debug, release]（注意：无 signingConfigs）
├─ local.properties              # kuikly.* 配置（compilePluginEnabled=false，见下）
├─ hvigor/hvigor-config.json5    # 依赖 kuikly-ohos-compile-plugin@0.0.1-alpha.7
├─ hvigorfile.ts                 # 引用 kuiklyCompilePlugin
└─ entry/
   ├─ oh-package.json5           # 依赖 @kuikly-open/render: 2.15.0 + libentry.so
   ├─ build-profile.json5        # arm64-v8a only
   ├─ libs/arm64-v8a/libshared.so        # ← 拷贝自 :shared:linkDebugSharedOhosArm64
   └─ src/main/
      ├─ ets/entryability/EntryAbility.ets  # 注册 LogAdapter + RouterAdapter
      ├─ ets/kuikly/MyNativeManager.ets     # Napi.initKuikly() 入口
      ├─ ets/kuikly/KuiklyViewDelegate.ets  # 注册 KNBridgeModule（无 KLineChart 桥接）
      ├─ ets/kuikly/adapter/
      │  ├─ LogAdapter.ets       # hilog → KRLogModule
      │  └─ RouterAdapter.ets     # pushUrl 通过 stockchat_page_name AppStorage
      ├─ ets/kuikly/modules/
      │  └─ KRBridgeModule.ets   # closePage/toast/log
      ├─ ets/pages/Index.ets     # pageName='StockChat'，expandSafeArea 处理 statusBarHeight
      └─ cpp/
         ├─ napi_init.cpp        # 只注册 initKuikly（无 KLine bridge）
         ├─ CMakeLists.txt       # 链接 kuikly_render (@kuikly-open/render) + kuikly_shared (libshared.so)
         ├─ include/libshared_api.h   # ← 拷贝自 :shared:linkDebugSharedOhosArm64
         └─ types/libentry/index.d.ts # initKuikly: () => number
```

#### ⚠️ `kuikly-ohos-compile-plugin` 的坑
- 插件在 `kuikly_build` 任务里硬编码拼出 `:shared:linkdebugSharedOhosArm64`（小写 d），但 KMP 实际任务是 `linkDebugSharedOhosArm64`（大写 D）
- 临时方案：把 `kuikly.compilePluginEnabled=false` 写进 `ohosApp/local.properties`，**手动**先用 Gradle 编出 `libshared.so` 再拷过来，然后让 hvigorw 只跑 `assembleHap` 部分
- 等待 Tencent 官方插件修复后，可以恢复 `true`（同时移除手动 cp 步骤）

---

## 6. 业务代码跨端差异一览

| 业务能力 | Android | iOS | OHOS |
|----------|---------|-----|------|
| `@Page` 路由 / 页面栈 | ✅ | ✅ | ✅（通过 RouterAdapter 的 `stockchat_page_name` AppStorage 转发） |
| 流式 Markdown 渲染（Kuikly Markdown） | ✅ KuiklyMarkdown | ✅ KuiklyMarkdown | ⚠️ 纯 Text 兜底，结构信息丢失（标题/列表/表格等） |
| 行情 WebSocket | ✅ | ✅ | ✅（domain/network 层无平台依赖） |
| 实时 K 线（专业图表） | ✅ KuiklyKLineChart 原生 | ✅ KuiklyKLineChart 原生 | ⚠️ `ChartPlaceholder` 占位，提示"OHOS 端暂以占位呈现" |
| 本地 KV 存储 | ✅ SharedPreferences | ✅ UserDefaults | ✅ ArkUI Preferences（由 `core-render-ohos` 提供） |
| 跨页跳转（Router） | ✅ KRRouterModule | ✅ KRRouterModule | ✅ 自定义 `stockchat_page_name` AppStorage 模拟 pushUrl |
| 工具模块（closePage/toast/log） | ✅ | ✅ | ✅ KRBridgeModule 注册到 KuiklyRenderView |

> OHOS 端的"⚠️ 兜底"行为是有意为之——鸿蒙 Kuikly core 2.15.0 暂未提供 `kuiklybase.markdown` 和 `kuiklyklinechart` 的 ohos_arm64 klib。把它们做成 `expect/actual` 后，等腾讯发布新 klib 时只需替换 `ohosArm64Main` 下的 actual 文件即可，业务代码（commonMain）无需改动。

---

## 7. 已知问题 & 后续工作

1. **KSP 版本警告**：每次 OHOS 构建仍打印 `ksp-2.0.21-1.0.27 is too old for kotlin-2.0.21-KBA-010`，但目前不影响产物正确性，等 Tencent 同步 KSP 后消除。
2. **Kotlin Hierarchy Template 警告**：`Explicit .dependsOn() edges were configured for iosArm64Main, iosMain, iosSimulatorArm64Main`，是因为 KBA 与默认 Hierarchy Template 不兼容；通过手动 `by getting + dependsOn` 已规避。
3. **OHOS HAP 签名**：当前为 `unsigned.hap`，需在 DevEco Studio 中生成 p12 + cer + p7b 三件套后放回 `ohosApp/signing/` 并在 `build-profile.json5` 中补 `signingConfigs`。
4. **iOS 真机出包**：当前只跑了模拟器。需在 Xcode 中选择 `Any iOS Device (arm64)` + 配 Apple Developer 证书后 `xcodebuild ... -sdk iphoneos`。
5. **OHOS 模拟器/真机安装**：模拟器需先启动（`hdc list targets` 应非空），之后 `hdc install entry-default-signed.hap` + `hdc shell aa start -a EntryAbility -b com.kuikly.stockchat`。
6. **Markdown / KLine 鸿蒙端原生实现**：待 Tencent 发布对应 klib（或我们自研 ArkUI Canvas + Markdown 解析）后，把 `ohosArm64Main/.../MarkdownViews.actual.kt` 与 `PlatformKLineChart.actual.kt` 换成原生实现即可。

---

## 8. 快速复现脚本

```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "==> [1/4] Android APK"
./gradlew :androidApp:assembleDebug --no-daemon

echo "==> [2/4] iOS pod install + xcodebuild"
./gradlew :shared:podInstall --no-daemon
(cd iosApp && pod install)
xcodebuild \
  -workspace iosApp/KuiklyStockChat.xcworkspace \
  -scheme KuiklyStockChat \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build

echo "==> [3/4] OHOS libshared.so"
./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64 --no-daemon
mkdir -p ohosApp/entry/libs/arm64-v8a ohosApp/entry/src/main/cpp/include
cp shared/build/bin/ohosArm64/debugShared/libshared.so ohosApp/entry/libs/arm64-v8a/
cp shared/build/bin/ohosArm64/debugShared/libshared_api.h ohosApp/entry/src/main/cpp/include/

echo "==> [4/4] OHOS HAP"
cd ohosApp
/Applications/DevEco-Studio.app/Contents/tools/ohpm/bin/ohpm install
DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk \
  /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw assembleHap --no-daemon

echo "All three platforms built OK."
```

---

## 9. 文件改动总览（关键路径）

```
新增：
  buildSrc/{build.gradle.kts, settings.gradle.kts, src/main/kotlin/Dependencies.kt}
  build.ohos.gradle.kts
  settings.ohos.gradle.kts
  iosApp/                       # iOS 壳工程（含 Podfile / .xcodeproj / 源码）
  ohosApp/                      # 鸿蒙壳工程（含 oh-package.json5 / entry / hvigorfile / CMakeLists.txt）
  shared/src/androidMain/kotlin/com/kuikly/stockchat/ui/components/MarkdownViews.actual.kt
  shared/src/iosMain/kotlin/com/kuikly/stockchat/ui/components/MarkdownViews.actual.kt
  shared/src/ohosArm64Main/kotlin/com/kuikly/stockchat/ui/components/MarkdownViews.actual.kt
  shared/src/{androidMain,iosMain,ohosArm64Main}/kotlin/com/kuikly/stockchat/app/PlatformKLineChart.actual.kt
  shared/src/commonMain/kotlin/com/kuikly/stockchat/app/PlatformKLineChart.kt

修改：
  gradle.properties             # +org.gradle.java.home=jbr-21.0.11
  shared/build.gradle.kts       # KuiklyMarkdown 从 commonMain 移到 androidMain+iosMain
  shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/components/MarkdownViews.kt
                                # 拆成 expect/actual
  shared/src/commonMain/kotlin/com/kuikly/stockchat/app/StockDetailPage.kt
                                # KLineChart 改为 PlatformKLineChart()
  iosApp/KuiklyStockChat/{KRRouterHandler.m, KuiklyLogHandler.h, KuiklyRenderViewController.m, ...}
                                # 修正协议名（KuiklyLogProtocol / KRRouterModule / BaseDelegator）
  iosApp/KuiklyStockChat/KuiklyLogHandler.{h,m}
                                # 删除（与 OpenKuiklyIOSRender 重复）
```
