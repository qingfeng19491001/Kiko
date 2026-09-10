package com.kuikly.stockchat.data.ai

/**
 * AI 大模型配置。
 *
 * 支持百炼按量付费与 Token Plan：`sk-sp-` 自动走 Token Plan 网关，其余走 dashscope。
 * API Key 放在 gitignore 的 [AiSecrets] 中：复制 `AiSecrets.kt.example` 为 `AiSecrets.kt` 后填写。
 */
object AiConfig {
    /** 百炼按量付费 OpenAI 兼容地址。 */
    const val PAYGO_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    /** Token Plan（个人/团队）OpenAI 兼容地址，必须与 `sk-sp-` 套餐 Key 配套。 */
    const val TOKEN_PLAN_BASE_URL = "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"

    /** 火山引擎双向流式语音服务。 */
    const val VOLC_ASR_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
    const val VOLC_TTS_URL = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    /** 百炼 API Key（来自本地 AiSecrets.kt，不入库） */
    val API_KEY: String get() = AiSecrets.API_KEY

    /** 火山引擎语音 API Key（来自本地 AiSecrets.kt，不入库）。 */
    val VOLC_API_KEY: String get() = AiSecrets.VOLC_API_KEY

    /**
     * 按 Key 类型选择网关：`sk-sp-` 走 Token Plan，其余走按量付费。
     * 两类 Key / Base URL 不能混用，否则会 401。
     */
    val BASE_URL: String
        get() = if (API_KEY.startsWith("sk-sp-")) TOKEN_PLAN_BASE_URL else PAYGO_BASE_URL

    /** 默认模型 */
    const val MODEL = "qwen3.8-flash"

    /** 请求超时（秒） */
    const val TIMEOUT_SECONDS = 30

    /** 采样温度：0.7 偏向稳健、可复现的分析输出 */
    const val TEMPERATURE = 0.7
}
