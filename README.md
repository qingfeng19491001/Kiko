# Kiko · kuikly-stock-chat

基于 **Kuikly + Kotlin Multiplatform** 的跨端 AI 股票应用。一套 `commonMain` 页面同时跑 Android / iOS / 鸿蒙：行情列表 → 个股详情 → AI问答。

2026 腾讯犀牛鸟开源人才计划 · KuiklyUI 实战作品。行情与 AI 结论仅供演示，**不构成投资建议**。

---

## 完成任务情况

| 课题 | 状态 | 交付 |
| --- | --- | --- |
| **Task 1 · AI 行情原型** | 已完成 | 行情首页、分市场切换、指数与总览、自选、个股详情与完整 K 线工作区 |
| **Task 2 · AI 股票问答** | 已完成 | 研究过程可见、结论+证据卡片、流式正文、附件/语音、详情选点追问 |

三端冷启动均为行情首页 `MarketList`。演示句：`腾讯控股在同行业里排名怎么样`。

### Task 1

- **行情列表**：名称、代码、最新价、涨跌额、涨跌幅；可滚、可搜、可点进详情。
- **七个市场**：全球 / 自选 / 指数 / A股 / 港股 / 港股通 / 美股；各 Tab 有对应指数 sparkline。
- **市场总览**：A 股与全球展示两市成交额和大小盘（沪深300 / 中证500 / 中证1000）；港股、港股通展示恒指成交额与恒指/恒科/国企；美股不硬凑两市数据。
- **真实行情**：腾讯 `qt` / `fqkline`。缺数显示 `--` 并提示不完整，**不用 Mock 价冒充实时**。
- **个股详情**：分时 / 五日 / 日周月 K，以及分钟与季年等扩展周期；一主图二副图（均线、成交量、MACD 等）。
- **页内承接**：诊股、简况、技术、资金、板块，避免一屏堆到底。
- **图表进聊天**：十字光标选中某根 K 线，带着时间与价格去问 AI。

### Task 2

- **真研究阶段**：理解问题 → 识别标的 → 读行情/日 K → 交叉分析 → 完成。阶段由请求推进，不是假倒计时；正文出来后进度条收起，来源可再展开。
- **结论先行、证据随后**：本地用真实行情拼卡片/图表，模型只写章节解读，再按「标题 → 文字 → 数据卡」穿插，避免卡片堆一堆、长文甩最后。
- **结构化回答**：行情卡、同行表、对比、K 线/柱状/多序列图、关键价位、风险与追问；点卡片进详情。
- **同业问答**：先给格局结论，再给可点进详情的对比表和阶段表现。
- **多模态**：图片、文档（`qwen-long`）、语音输入与朗读。
- **收束操作**：朗读、赞踩、系统分享、重新生成；失败可重试，不把错误伪装成分析。

---

## 亮点

| 亮点 | 说明 |
| --- | --- |
| **三端一套页** | 4 个 `@Page` 全在共享层；宿主只做容器和附件 / 分享 / 语音桥。Android、iOS、鸿蒙均可出包。 |
| **行情不造假** | 首页禁止 Mock 价。没有全市场成交明细就不画假涨跌分布、假主力。连板/广度/资金只在本地 AKShare 网关有数时出现。 |
| **卡片是证据，模型是文案** | `AnswerComposer` 用快照和技术分析出可复现数字；百炼按章节写解读；`AnswerAssembler` 把两段缝成一篇研报。Key 缺失或模型失败直接说原因，不换套话。 |
| **研究过程可核对** | 进度和「用了几类数据源、几个标的、多少根日 K」来自本轮真实调用，不伪造网页检索条数。 |
| **图表和对话打通** | 详情十字光标选点 → 带 OHLC 进聊天；回答里的行情卡 / 同行表再点回详情。 |
| **K 线是工作区不是贴图** | 多周期、主图+双副图、指标切换；日 K 预测只在模型校验通过后叠加，失败不画假曲线。 |
| **美股指数能画出历史** | 报价走 `usDJI`，K 线走 `us.DJI`，避免腾讯单根棒。 |
| **组件与业务拆仓** | `:components` 无 `@Page`，图表 / 表格 / Markdown 独立，避免 KSP 入口冲突。 |
| **合仓三端可跑** | Task 1 与 Task 2 在同一仓库交付；行情与卡片数字可回溯到接口。 |

---

## 快速运行

```bash
cp shared/src/commonMain/kotlin/com/kuikly/stockchat/data/ai/AiSecrets.kt.example \
   shared/src/commonMain/kotlin/com/kuikly/stockchat/data/ai/AiSecrets.kt
# 填写百炼 API_KEY；语音 / iTick 可空

./gradlew :androidApp:assembleDebug
# APK：androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

直达聊天：`adb shell am start -n com.kuikly.stockchat/com.kuikly.stockchat.android.KuiklyRenderActivity --es pageName StockChat`

iOS：`./gradlew :shared:podInstall` 后 `iosApp` `pod install`，根页已是 `MarketList`。鸿蒙用 DevEco 打开 `ohosApp`。

可选：`python scripts/ak_gateway.py`（`127.0.0.1:8790`）后才有连板 / 广度 / 资金卡。

密钥不要提交。`sk-sp-` 走 Token Plan，其余走 DashScope。
