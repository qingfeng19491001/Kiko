package com.kuikly.stockchat.data.ai

actual class VoicePlatformService actual constructor(apiKey: String, baseUrl: String) {
    actual fun startRecording(onResult: (VoiceOperationResult) -> Unit) =
        VoiceBridge.startRecording(onResult)

    actual fun stopRecording(onResult: (VoiceTranscriptionResult) -> Unit) =
        VoiceBridge.stopRecording(onResult)

    actual fun cancelRecording() = VoiceBridge.cancelRecording()

    actual fun speak(text: String, onResult: (VoiceOperationResult) -> Unit) =
        VoiceBridge.speak(text, onResult)

    actual fun stopSpeaking() = VoiceBridge.stopSpeaking()
}
