package com.kuikly.stockchat.data.ai

actual class VoicePlatformService actual constructor(apiKey: String, baseUrl: String) {
    actual fun startRecording(onResult: (VoiceOperationResult) -> Unit) =
        onResult(VoiceOperationResult(false, "iOS 语音录入正在适配"))

    actual fun stopRecording(onResult: (VoiceTranscriptionResult) -> Unit) =
        onResult(VoiceTranscriptionResult(error = "iOS 语音录入正在适配"))

    actual fun cancelRecording() = Unit
    actual fun speak(text: String, onResult: (VoiceOperationResult) -> Unit) =
        onResult(VoiceOperationResult(false, "iOS 语音播报正在适配"))

    actual fun stopSpeaking() = Unit
}
