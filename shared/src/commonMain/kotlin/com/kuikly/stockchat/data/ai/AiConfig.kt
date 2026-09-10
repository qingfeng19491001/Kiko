package com.kuikly.stockchat.data.ai

/**
 * AI 大模型配置。
 *
 * 默认接入阿里云百炼（DashScope）OpenAI 兼容模式。
 * 可在构造 [RemoteAiEngine] 时覆盖这些默认值。
 *
 * API Key 放在 gitignore 的 [AiSecrets] 中：复制 `AiSecrets.kt.example` 为 `AiSecrets.kt` 后填写。
 */
object AiConfig {
    /** 百炼 OpenAI 兼容模式 Base URL */
    const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    /** 火山引擎双向流式语音服务。 */
    const val VOLC_ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
    const val VOLC_TTS_URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    /** 百炼 API Key（来自本地 AiSecrets.kt，不入库） */
    val API_KEY: String get() = AiSecrets.API_KEY

    /** 火山引擎语音 API Key（来自本地 AiSecrets.kt，不入库）。 */
    val VOLC_API_KEY: String get() = AiSecrets.VOLC_API_KEY

    /** iTick REST Token（来自本地 AiSecrets.kt，不入库）。空则详情分时回退腾讯。 */
    val ITICK_TOKEN: String get() = AiSecrets.ITICK_TOKEN

    /** 默认模型 */
    const val MODEL = "qwen3.8-flash"

    /** 请求超时（秒） */
    const val TIMEOUT_SECONDS = 30

    /** 采样温度：0.7 偏向稳健、可复现的分析输出 */
    const val TEMPERATURE = 0.7
}
