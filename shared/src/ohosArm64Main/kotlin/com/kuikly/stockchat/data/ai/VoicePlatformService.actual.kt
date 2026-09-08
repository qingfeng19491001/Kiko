package com.kuikly.stockchat.data.ai

actual class VoicePlatformService actual constructor(apiKey: String, baseUrl: String) {
    actual fun startRecording(onResult: (VoiceOperationResult) -> Unit) =
        onResult(VoiceOperationResult(false, "当前平台暂不支持语音录入"))

    actual fun stopRecording(onResult: (VoiceTranscriptionResult) -> Unit) =
        onResult(VoiceTranscriptionResult(error = "当前平台暂不支持语音录入"))

    actual fun cancelRecording() = Unit
    actual fun speak(text: String, onResult: (VoiceOperationResult) -> Unit) =
        onResult(VoiceOperationResult(false, "当前平台暂不支持语音播报"))

    actual fun stopSpeaking() = Unit
}
