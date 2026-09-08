# DeepSeek 式附件面板与多模态附件设计

## 目标

将当前附件入口优化为参考视频中的 DeepSeek 式底部附件面板，并接入 Android、iOS、鸿蒙的真实拍照、相册和文件选择；用户可以把多个附件与文本一起发送给阿里云模型，历史会话和重新生成继续保留附件。

## 已确认的交互

- 附件面板替代键盘所在区域，不显示内容区暗色遮罩。
- 打开面板时输入框随面板上移；加号变为关闭按钮。
- 面板只显示“拍照”“相册”“文件”三个入口，不显示参考视频中的最近照片列表。
- 选择完成后，附件进入输入框内部上方的待发送附件条：图片显示缩略图，文档显示文件卡片；支持多附件和逐项删除。
- 用户可以继续输入、继续添加或删除附件，点击发送统一提交；纯附件也允许发送。
- 取消选择器不改变状态；权限、读取、复制、上传或模型失败保留待发送项并显示错误态。
- 历史用户消息保留附件卡片；重新生成重新读取原附件。失效附件阻止重新生成并提示。

## 公共模型与平台边界

公共层定义 `AttachmentModule`，提供异步 `openCamera`、`openPhotoLibrary`、`openFilePicker`；平台实现负责权限、系统选择器、将结果复制到应用私有附件目录，并回传稳定的 `Attachment` 元数据和本地路径。

`Attachment` 至少包含 `id`、`displayName`、`mimeType`、`byteSize`、`localPath`、可选 `thumbnailPath`、来源和类型。`ChatMessage.attachments` 与 `ChatCodec` 持久化这些字段。系统 URI、security-scoped URL 等只在平台层使用，不进入公共历史模型。

Android 使用 Activity Result API 与 SAF；iOS 使用 `UIImagePickerController`/`PHPickerViewController`/`UIDocumentPickerViewController`；鸿蒙使用对应相机、图库和文档选择 API。平台注册沿用 Kuikly Module 扩展机制。

## AI 传输策略

当前配置的 `qwen3.8-flash` 支持 OpenAI 兼容 Chat API 的多模态 `content` 数组和 `image_url` Base64 Data URL，图片最大约 1600 万像素、最多 2048 张，默认图像处理上限约 262 万像素。图片在端上生成受控副本后发送。

文档理解当前仅由 `qwen-long` 支持：通过兼容接口的 `/files` 上传并以 `fileid://` 引用。图片与文档混合时，先并行上传/提取文档，再把提取文本、图片和用户问题交给 `qwen3.8-flash`。实际文件格式和字节上限以服务端响应为准，不能在客户端伪造硬编码模型限制。

文本-only 请求保持现有模型路径。附件请求使用多段消息内容；无文字时补充最小默认问题。上传的远端文件在请求结束后清理，本地副本随会话保留。

## 状态与错误

键盘、附件面板、语音模式互斥；返回键优先关闭当前附件/语音状态。发送条件为文本非空或附件列表非空。附件存在 `READY`、`PROCESSING`、`FAILED`、`EXPIRED` 状态。失败项不能被静默降级为文本，用户可移除失败项后重试。

## 测试

- 公共单测覆盖附件发送条件、状态转换、编码解码、图片/文档/混合请求编排和失败阻断。
- Android、iOS、鸿蒙分别验证三类选择器、权限拒绝、稳定副本和 Module 回调。
- 端到端验证图片、模型支持的文档、图片+文档、纯附件、历史恢复、重新生成与失效附件。
