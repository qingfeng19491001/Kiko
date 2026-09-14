package com.kuikly.stockchat.data.ai

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Android implementation of Volcengine bidirectional ASR and TTS WebSocket protocols. */
actual class VoicePlatformService actual constructor(
    private val apiKey: String,
    private val baseUrl: String,
) {
    private var recorder: AudioRecord? = null
    private var recordingFile: File? = null
    private var recordingThread: Thread? = null
    private val recordingActive = AtomicBoolean(false)
    private var speechSocket: WebSocket? = null
    private var audioTrack: AudioTrack? = null

    actual fun startRecording(onResult: (VoiceOperationResult) -> Unit) {
        val context = AndroidPlatformContext.applicationContext
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AndroidPlatformContext.activity()?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            onResult(VoiceOperationResult(false, "请允许麦克风权限后再试"))
            return
        }
        runCatching {
            cancelRecording()
            File.createTempFile("stockchat_voice_", ".pcm", context.cacheDir).also { output ->
                val bufferSize = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4_096)
                val audioRecorder = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                recorder = audioRecorder
                recordingFile = output
                recordingActive.set(true)
                audioRecorder.startRecording()
                recordingThread = Thread {
                    FileOutputStream(output).use { stream ->
                        val buffer = ByteArray(bufferSize)
                        while (recordingActive.get()) {
                            val count = audioRecorder.read(buffer, 0, buffer.size)
                            if (count > 0) stream.write(buffer, 0, count)
                        }
                    }
                }.also { it.start() }
            }
        }.fold(
            onSuccess = { onResult(VoiceOperationResult(true)) },
            onFailure = { onResult(VoiceOperationResult(false, "无法启动录音：${it.message ?: "请重试"}")) },
        )
    }

    actual fun stopRecording(onResult: (VoiceTranscriptionResult) -> Unit) {
        val file = recordingFile
        val activeRecorder = recorder
        recorder = null
        recordingFile = null
        if (file == null || activeRecorder == null) return onResult(VoiceTranscriptionResult(error = "未获取到有效录音"))
        recordingActive.set(false)
        val stopped = runCatching { activeRecorder.stop(); recordingThread?.join(800); activeRecorder.release() }
        recordingThread = null
        if (stopped.isFailure || file.length() < MIN_AUDIO_BYTES) {
            file.delete()
            return onResult(VoiceTranscriptionResult(error = "录音时间过短，请按住后再说话"))
        }
        Thread {
            val wavFile = runCatching { pcmToWav(file) }.getOrElse { file.delete(); return@Thread onResult(VoiceTranscriptionResult(error = "无法整理录音：${it.message}")) }
            val result = runCatching { transcribe(wavFile) }
                .fold({ VoiceTranscriptionResult(text = it) }, { VoiceTranscriptionResult(error = "语音识别失败：${it.message ?: "请检查网络"}") })
            wavFile.delete()
            onResult(result)
        }.start()
    }

    actual fun cancelRecording() {
        val activeRecorder = recorder
        recorder = null
        recordingActive.set(false)
        runCatching { activeRecorder?.stop(); recordingThread?.join(300); activeRecorder?.release() }
        recordingThread = null
        recordingFile?.delete()
        recordingFile = null
    }

    actual fun speak(text: String, onResult: (VoiceOperationResult) -> Unit) {
        if (text.isBlank()) return onResult(VoiceOperationResult(true))
        stopSpeaking()
        Thread {
            runCatching { synthesize(text.take(MAX_TTS_CHARS)) }
                .fold({ onResult(VoiceOperationResult(true)) }, { onResult(VoiceOperationResult(false, "语音播报失败：${it.message ?: "请检查服务配置"}")) })
        }.start()
    }

    actual fun stopSpeaking() {
        speechSocket?.close(1000, "stop")
        speechSocket = null
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }
        audioTrack = null
    }

    private fun transcribe(audioFile: File): String {
        require(apiKey.isNotBlank()) { "未配置火山引擎语音 API Key" }
        val complete = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val recognizedText = AtomicReference("")
        val audio = audioFile.readBytes()
        val request = Request.Builder().url(baseUrl)
            .header("X-Api-Key", apiKey).header("X-Api-Resource-Id", ASR_RESOURCE_ID)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
            .header("X-Api-Connect-Id", UUID.randomUUID().toString()).build()
        val socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject().apply {
                    put("user", JSONObject().put("uid", "stockchat_${UUID.randomUUID()}"))
                    put("audio", JSONObject().apply { put("format", "wav"); put("codec", "raw"); put("rate", 16_000); put("bits", 16); put("channel", 1) })
                    put("request", JSONObject().apply {
                        put("model_name", "bigmodel"); put("enable_itn", true); put("enable_punc", true)
                        put("enable_ddc", true); put("enable_nonstream", true)
                        put("corpus", JSONObject().put("context", STOCK_HOTWORDS))
                    })
                }
                webSocket.send(VolcFrame.asrFullRequest(1, payload.toString()))
                var sequence = 2
                var offset = 0
                while (offset < audio.size) {
                    val end = (offset + ASR_CHUNK_BYTES).coerceAtMost(audio.size)
                    val last = end == audio.size
                    webSocket.send(VolcFrame.asrAudioRequest(sequence, audio.copyOfRange(offset, end), last))
                    if (!last) sequence++
                    offset = end
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val response = VolcFrame.parseAsr(bytes.toByteArray())
                response.error?.let { failure.set(it); complete.countDown(); return }
                response.text?.takeIf { it.isNotBlank() }?.let { recognizedText.set(it) }
                if (response.last) complete.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure.set(response?.let { "连接失败 ${it.code}" } ?: t.message ?: "无法连接语音服务")
                complete.countDown()
            }
        })
        if (!complete.await(ASR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw IllegalStateException("识别超时")
        socket.close(1000, "complete")
        failure.get()?.let { throw IllegalStateException(it) }
        return recognizedText.get().trim().ifBlank { throw IllegalStateException("服务未返回识别文本") }
    }

    private fun synthesize(text: String) {
        require(apiKey.isNotBlank()) { "未配置火山引擎语音 API Key" }
        val complete = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val sessionId = UUID.randomUUID().toString()
        val audioStarted = AtomicBoolean(false)
        val request = Request.Builder().url(AiConfig.VOLC_TTS_URL)
            .header("X-Api-Key", apiKey).header("X-Api-Resource-Id", TTS_RESOURCE_ID)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString()).build()
        speechSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(VolcFrame.event(EVENT_START_CONNECTION, null, "{}"))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = VolcFrame.parseTts(bytes.toByteArray())
                when (frame.event) {
                    EVENT_CONNECTION_STARTED -> webSocket.send(VolcFrame.event(EVENT_START_SESSION, sessionId, ttsSessionPayload()))
                    EVENT_SESSION_STARTED -> webSocket.send(VolcFrame.event(EVENT_TASK_REQUEST, sessionId, JSONObject().put("text", text).toString()))
                    EVENT_SESSION_FINISHED, EVENT_SESSION_CANCELED -> complete.countDown()
                    EVENT_SESSION_FAILED, EVENT_CONNECTION_FAILED -> { failure.set(frame.payloadText.ifBlank { "语音服务拒绝了请求" }); complete.countDown() }
                }
                if (frame.audio.isNotEmpty()) {
                    if (audioStarted.compareAndSet(false, true)) startPcmPlayback()
                    audioTrack?.write(frame.audio, 0, frame.audio.size)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure.set(response?.let { "连接失败 ${it.code}" } ?: t.message ?: "无法连接语音服务")
                complete.countDown()
            }
        })
        if (!complete.await(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw IllegalStateException("播报超时")
        speechSocket?.close(1000, "complete")
        speechSocket = null
        failure.get()?.let { throw IllegalStateException(it) }
    }

    private fun ttsSessionPayload(): String = JSONObject().apply {
        put("req_params", JSONObject().apply {
            put("speaker", TTS_SPEAKER)
            put("audio_params", JSONObject().apply { put("format", "pcm"); put("sample_rate", 24_000) })
            put("disable_markdown_filter", true)
            put("explicit_language", "zh-cn")
        })
    }.toString()

    private fun pcmToWav(pcmFile: File): File {
        val pcm = pcmFile.readBytes()
        val wav = File.createTempFile("stockchat_voice_", ".wav", pcmFile.parentFile)
        FileOutputStream(wav).use { output ->
            output.write("RIFF".toByteArray())
            output.writeLittleEndian(36 + pcm.size)
            output.write("WAVEfmt ".toByteArray())
            output.writeLittleEndian(16); output.writeLittleEndianShort(1); output.writeLittleEndianShort(1)
            output.writeLittleEndian(16_000); output.writeLittleEndian(32_000)
            output.writeLittleEndianShort(2); output.writeLittleEndianShort(16)
            output.write("data".toByteArray()); output.writeLittleEndian(pcm.size); output.write(pcm)
        }
        pcmFile.delete()
        return wav
    }

    private fun startPcmPlayback() {
        runCatching { audioTrack?.release() }
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(PCM_BUFFER_BYTES).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 3001
        const val MIN_AUDIO_BYTES = 1_024L
        const val MAX_TTS_CHARS = 800
        const val ASR_TIMEOUT_SECONDS = 50L
        const val TTS_TIMEOUT_SECONDS = 60L
        const val ASR_CHUNK_BYTES = 4_096
        const val PCM_BUFFER_BYTES = 96_000
        const val ASR_RESOURCE_ID = "volc.seedasr.sauc.duration"
        const val TTS_RESOURCE_ID = "seed-tts-2.0"
        const val TTS_SPEAKER = "zh_female_vv_uranus_bigtts"
        const val EVENT_START_CONNECTION = 1
        const val EVENT_CONNECTION_STARTED = 50
        const val EVENT_CONNECTION_FAILED = 51
        const val EVENT_START_SESSION = 100
        const val EVENT_SESSION_STARTED = 150
        const val EVENT_SESSION_CANCELED = 151
        const val EVENT_SESSION_FINISHED = 152
        const val EVENT_SESSION_FAILED = 153
        const val EVENT_TASK_REQUEST = 200
        const val STOCK_HOTWORDS = "{\"hotwords\":[{\"word\":\"腾讯控股\"},{\"word\":\"恒生指数\"},{\"word\":\"上证指数\"},{\"word\":\"市盈率\"},{\"word\":\"贵州茅台\"}]}"
        val httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }
}

private fun FileOutputStream.writeLittleEndian(value: Int) = write(byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte()))
private fun FileOutputStream.writeLittleEndianShort(value: Int) = write(byteArrayOf(value.toByte(), (value ushr 8).toByte()))

/** Volcengine v3 binary frame encoder/decoder shared by streaming ASR and TTS. */
private object VolcFrame {
    private const val TYPE_FULL_CLIENT = 0x1
    private const val TYPE_AUDIO_CLIENT = 0x2
    private const val TYPE_FULL_SERVER = 0x9
    private const val TYPE_AUDIO_SERVER = 0xB
    private const val TYPE_ERROR = 0xF
    private const val FLAG_POSITIVE_SEQUENCE = 0x1
    private const val FLAG_NEGATIVE_SEQUENCE = 0x3
    private const val FLAG_WITH_EVENT = 0x4
    private const val SERIALIZATION_JSON = 0x1
    private const val COMPRESSION_GZIP = 0x1

    fun asrFullRequest(sequence: Int, json: String): ByteString = ByteString.of(*frame(TYPE_FULL_CLIENT, FLAG_POSITIVE_SEQUENCE, SERIALIZATION_JSON, COMPRESSION_GZIP, sequence, gzip(json.toByteArray())))
    fun asrAudioRequest(sequence: Int, audio: ByteArray, last: Boolean): ByteString = ByteString.of(*frame(TYPE_AUDIO_CLIENT, if (last) FLAG_NEGATIVE_SEQUENCE else FLAG_POSITIVE_SEQUENCE, SERIALIZATION_JSON, COMPRESSION_GZIP, if (last) -sequence else sequence, gzip(audio)))

    fun event(event: Int, sessionId: String?, json: String): ByteString {
        val payload = json.toByteArray()
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0x11, ((TYPE_FULL_CLIENT shl 4) or FLAG_WITH_EVENT).toByte(), 0x10, 0))
        output.writeInt(event)
        if (sessionId != null) { val id = sessionId.toByteArray(); output.writeInt(id.size); output.write(id) }
        output.writeInt(payload.size); output.write(payload)
        return ByteString.of(*output.toByteArray())
    }

    fun parseAsr(bytes: ByteArray): AsrResult {
        val parsed = parse(bytes)
        if (parsed.type == TYPE_ERROR) return AsrResult(error = parsed.payloadText)
        val root = runCatching { JSONObject(parsed.payloadText) }.getOrNull() ?: return AsrResult(last = parsed.last)
        val text = VolcAsrText.extract(parsed.payloadText)
        val code = root.optInt("code", 0)
        return AsrResult(text, parsed.last || root.optBoolean("is_last_package"), if (code == 0) null else root.optString("message", "服务错误 $code"))
    }

    fun parseTts(bytes: ByteArray): TtsResult {
        val parsed = parse(bytes)
        return TtsResult(parsed.event, if (parsed.type == TYPE_AUDIO_SERVER) parsed.payload else ByteArray(0), parsed.payloadText)
    }

    private fun frame(type: Int, flag: Int, serialization: Int, compression: Int, sequence: Int, payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0x11, ((type shl 4) or flag).toByte(), ((serialization shl 4) or compression).toByte(), 0))
        output.writeInt(sequence); output.writeInt(payload.size); output.write(payload)
        return output.toByteArray()
    }

    private fun parse(bytes: ByteArray): ParsedFrame {
        require(bytes.size >= 4) { "服务返回无效数据" }
        var offset = (bytes[0].toInt() and 0x0F) * 4
        val type = (bytes[1].toInt() ushr 4) and 0x0F
        val flag = bytes[1].toInt() and 0x0F
        val compression = bytes[2].toInt() and 0x0F
        if ((flag and 0x01) != 0) offset += 4
        var event = 0
        if ((flag and FLAG_WITH_EVENT) != 0) {
            event = bytes.readInt(offset); offset += 4
            if (event !in setOf(1, 2, 50, 51, 52)) { val size = bytes.readInt(offset); offset += 4 + size }
        }
        if (type == TYPE_ERROR) offset += 4
        val size = bytes.readInt(offset); offset += 4
        val rawPayload = bytes.copyOfRange(offset, (offset + size).coerceAtMost(bytes.size))
        return ParsedFrame(type, event, (flag and 0x02) != 0, if (compression == COMPRESSION_GZIP) gunzip(rawPayload) else rawPayload)
    }

    private fun gzip(data: ByteArray): ByteArray = ByteArrayOutputStream().use { output -> GZIPOutputStream(output).use { it.write(data) }; output.toByteArray() }
    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    private fun ByteArray.readInt(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 24) or ((this[offset + 1].toInt() and 0xFF) shl 16) or ((this[offset + 2].toInt() and 0xFF) shl 8) or (this[offset + 3].toInt() and 0xFF)
    private fun ByteArrayOutputStream.writeInt(value: Int) { write(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())) }
    private data class ParsedFrame(val type: Int, val event: Int, val last: Boolean, val payload: ByteArray) { val payloadText get() = payload.toString(Charsets.UTF_8) }
    data class AsrResult(val text: String? = null, val last: Boolean = false, val error: String? = null)
    data class TtsResult(val event: Int, val audio: ByteArray, val payloadText: String)
}
