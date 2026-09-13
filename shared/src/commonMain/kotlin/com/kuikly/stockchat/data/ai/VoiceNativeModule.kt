package com.kuikly.stockchat.data.ai

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** Native ASR/TTS bridge used by iOS and HarmonyOS VoicePlatformService. */
class VoiceNativeModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    fun startListening(callback: CallbackFn) =
        asyncToNativeMethod("startListening", JSONObject(), callback)

    fun finishListening(callback: CallbackFn) =
        asyncToNativeMethod("finishListening", JSONObject(), callback)

    fun cancelListening() =
        asyncToNativeMethod("cancelListening", JSONObject(), null)

    fun speak(text: String, callback: CallbackFn) =
        asyncToNativeMethod("speak", JSONObject().apply { put("text", text) }, callback)

    fun stopSpeaking() =
        asyncToNativeMethod("stopSpeaking", JSONObject(), null)

    companion object {
        const val MODULE_NAME = "KRVoiceModule"
    }
}

/** Holds the pager-bound native voice module for platforms without a KMP audio stack. */
object VoiceBridge {
    var native: VoiceNativeModule? = null

    fun startRecording(onResult: (VoiceOperationResult) -> Unit) {
        val module = native ?: return onResult(VoiceOperationResult(false, "语音模块未就绪"))
        module.startListening { json ->
            onResult(
                VoiceOperationResult(
                    json?.optBoolean("success", false) == true,
                    json?.optString("message").orEmpty(),
                ),
            )
        }
    }

    fun stopRecording(onResult: (VoiceTranscriptionResult) -> Unit) {
        val module = native ?: return onResult(VoiceTranscriptionResult(error = "语音模块未就绪"))
        module.finishListening { json ->
            val text = json?.optString("text").orEmpty().ifBlank { null }
            val error = json?.optString("error").orEmpty().ifBlank { null }
            onResult(VoiceTranscriptionResult(text = text, error = error))
        }
    }

    fun cancelRecording() {
        native?.cancelListening()
    }

    fun speak(text: String, onResult: (VoiceOperationResult) -> Unit) {
        val module = native ?: return onResult(VoiceOperationResult(false, "语音模块未就绪"))
        module.speak(text) { json ->
            onResult(
                VoiceOperationResult(
                    json?.optBoolean("success", false) == true,
                    json?.optString("message").orEmpty(),
                ),
            )
        }
    }

    fun stopSpeaking() {
        native?.stopSpeaking()
    }
}
