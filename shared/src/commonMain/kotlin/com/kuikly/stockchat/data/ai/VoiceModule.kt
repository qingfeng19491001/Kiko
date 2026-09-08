package com.kuikly.stockchat.data.ai

/**
 * 面向业务页面的跨端语音模块。
 *
 * 页面只依赖本类，不接触火山引擎协议、Android 音频 API 或各平台权限细节。
 * Android、iOS、鸿蒙分别在 [VoicePlatformService] 的 actual 实现中接入原生能力。
 */
class VoiceModule private constructor(
    private val platformService: VoicePlatformService,
) {
    fun startListening(onResult: (VoiceOperationResult) -> Unit) =
        platformService.startRecording(onResult)

    fun finishListening(onResult: (VoiceTranscriptionResult) -> Unit) =
        platformService.stopRecording(onResult)

    fun cancelListening() = platformService.cancelRecording()

    fun speak(text: String, onResult: (VoiceOperationResult) -> Unit) =
        platformService.speak(text, onResult)

    fun stopSpeaking() = platformService.stopSpeaking()

    fun release() {
        cancelListening()
        stopSpeaking()
    }

    companion object {
        /** 统一创建入口；后续更换服务商或音色时只修改模块配置。 */
        fun createDefault(): VoiceModule = VoiceModule(
            VoicePlatformService(AiConfig.VOLC_API_KEY, AiConfig.VOLC_ASR_URL),
        )
    }
}
