package com.kuikly.stockchat.data.ai

/**
 * 平台语音能力入口。
 *
 * Android 使用火山引擎双向 WebSocket 协议完成录音识别和语音合成，其他平台可按各自原生能力补齐 actual 实现。
 */
expect class VoicePlatformService(apiKey: String, baseUrl: String) {
    fun startRecording(onResult: (VoiceOperationResult) -> Unit)
    fun stopRecording(onResult: (VoiceTranscriptionResult) -> Unit)
    fun cancelRecording()
    fun speak(text: String, onResult: (VoiceOperationResult) -> Unit)
    fun stopSpeaking()
}

data class VoiceOperationResult(
    val success: Boolean,
    val message: String = "",
)

data class VoiceTranscriptionResult(
    val text: String? = null,
    val error: String? = null,
)
