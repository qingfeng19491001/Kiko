package com.kuikly.stockchat.ui.chat

import com.kuikly.stockchat.data.ai.VoiceModule
import com.kuikly.stockchat.data.attachment.AttachmentModule
import com.kuikly.stockchat.data.share.ContentActionModule
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.domain.attachment.AttachmentSource
import com.kuikly.stockchat.domain.attachment.AttachmentStatus
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.abs

internal const val PAN_SOURCE_HOME = 1
internal const val PAN_SOURCE_CLOSE = 2

internal interface StockChatRuntime {
    val voiceModule: VoiceModule
    fun after(ms: Int, block: () -> Unit)
    fun now(): Long
    fun pageWidth(): Float
    fun pageHeight(): Float
    fun bottomInset(): Float
    fun attachmentModule(): AttachmentModule
    fun shareModule(): ContentActionModule
    fun addBackCallback(callback: BackPressCallback)
    fun removeBackCallback(callback: BackPressCallback)
    fun containsBackCallback(callback: BackPressCallback): Boolean
}

/** 抽屉 / 语音 / 附件交互，Page 只负责生命周期与路由。 */
internal class StockChatActions(
    private val host: StockChatScreenHost,
    private val runtime: StockChatRuntime,
) {
    private val vm get() = host.chatViewModel
    private val drawer get() = host.drawerState

    private val drawerBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() = closeDrawer()
    }
    private val accessoryBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            host.attachmentPanelVisible = false
            host.voiceMode = false
            abortVoiceRecording()
            syncAccessoryBackHandler()
        }
    }

    private var motionGeneration = 0
    private var targetProgress = 0f
    private var voiceWaveGeneration = 0
    private var panStartX = 0f
    private var panStartY = 0f
    private var panStartProgress = 0f
    private var panResumeTarget = 0f
    private var panLastX = 0f
    private var panLastTime = 0L
    private var panVelocityX = 0f
    private var panLocked = false
    private var clickGeneration = 0
    private var voiceStartGeneration = 0
    private var voiceGestureActive = false

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        host.attachmentPanelVisible = false
        abortVoiceRecording()
        syncAccessoryBackHandler()
        host.followBottom = true
        host.inputRef?.view?.setText("")
        vm.send(text)
    }

    fun dismissKeyboard() {
        host.composerFocused = false
        host.inputRef?.view?.blur()
    }

    fun openDrawer() {
        host.attachmentPanelVisible = false
        host.voiceMode = false
        abortVoiceRecording()
        syncAccessoryBackHandler()
        dismissKeyboard()
        settleDrawer(1f)
    }

    fun closeDrawer() {
        if (vm.drawerSearchOpen) {
            vm.drawerSearchOpen = false
            vm.onDrawerQueryChange("")
        }
        settleDrawer(0f)
    }

    fun openDrawerSkill(skill: DrawerSkill) {
        closeDrawer()
        when (skill) {
            DrawerSkill.SCHEDULE -> {
                vm.newConversation()
                sendPrompt(PromptBank.SCHEDULE_PROMPT)
            }
            DrawerSkill.MEMORY -> {
                vm.newConversation()
                sendPrompt(PromptBank.memoryPrompt(vm.watchlistNames()))
            }
            DrawerSkill.RESEARCH -> {
                vm.newConversation()
                sendPrompt(PromptBank.RESEARCH_PROMPT)
            }
            DrawerSkill.PLAZA -> {
                vm.newConversation()
                sendPrompt(PromptBank.PLAZA_PROMPT)
            }
        }
    }

    fun settleDrawer(target: Float) {
        if (drawer.settling && targetProgress == target && !drawer.dragging) return
        val generation = ++motionGeneration
        targetProgress = target
        drawer.dragging = false
        host.panSource = 0
        val from = drawer.progress
        drawer.settling = abs(from - target) > 0.0001f
        if (vm.showHistory != (target == 1f)) vm.showHistory = target == 1f
        syncDrawerBackHandler()
        if (!drawer.settling) {
            drawer.progress = target
            syncDrawerBackHandler()
            return
        }
        val started = runtime.now()
        fun frame() {
            if (generation != motionGeneration) return
            val time = ((runtime.now() - started) / 360f).coerceIn(0f, 1f)
            drawer.progress = from + (target - from) * DrawerPhysics.ease(time)
            if (time < 1f) {
                runtime.after(16) { frame() }
            } else {
                drawer.progress = target
                drawer.settling = false
                syncDrawerBackHandler()
            }
        }
        runtime.after(16) { frame() }
    }

    fun syncDrawerBackHandler() {
        if (vm.showHistory || drawer.progress > 0f || drawer.settling || drawer.dragging) {
            if (!runtime.containsBackCallback(drawerBackCallback)) {
                runtime.addBackCallback(drawerBackCallback)
            }
        } else {
            runtime.removeBackCallback(drawerBackCallback)
        }
    }

    fun toggleTts() {
        vm.persistTtsEnabled(!vm.ttsEnabled)
        if (!vm.ttsEnabled) runtime.voiceModule.stopSpeaking()
        vm.banner = if (vm.ttsEnabled) "语音播报已开启，下一条回复将自动朗读" else "语音播报已关闭"
    }

    fun speakMessage(message: ChatUiMessage) {
        if (message.isSpeaking) {
            runtime.voiceModule.stopSpeaking()
            message.isSpeaking = false
            return
        }
        runtime.voiceModule.stopSpeaking()
        vm.messages.forEach { it.isSpeaking = false }
        val text = message.actionText()
        if (text.isBlank()) {
            vm.banner = "当前回复暂无可朗读内容"
            return
        }
        message.isSpeaking = true
        runtime.voiceModule.speak(text) { result ->
            runtime.after(0) {
                message.isSpeaking = false
                if (!result.success) vm.banner = result.message
            }
        }
    }

    fun feedback(message: ChatUiMessage, value: Int) {
        message.feedback = if (message.feedback == value) 0 else value
        vm.banner = when (message.feedback) {
            1 -> "感谢反馈，这条回答已标记为有帮助"
            -1 -> "已收到反馈，我们会继续改进回答"
            else -> "已取消本次评价"
        }
    }

    fun shareMessage(message: ChatUiMessage) {
        val text = message.actionText()
        if (text.isBlank()) {
            vm.banner = "当前回复暂无可分享内容"
            return
        }
        runtime.shareModule().share(
            title = "Kiko AI 股票解读",
            text = "$text\n\n行情有时效性，不构成投资建议。",
        ) { result ->
            runtime.after(0) {
                if (result?.optBoolean("success", false) == false) {
                    vm.banner = result.optString("error").ifEmpty { "分享失败，请稍后重试" }
                }
            }
        }
    }

    fun copyText(text: String) {
        if (text.isBlank()) return
        runtime.shareModule().copy(text) { result ->
            runtime.after(0) {
                vm.banner = if (result?.optBoolean("success", false) == true) {
                    "已复制选中文字"
                } else {
                    result?.optString("error").orEmpty().ifBlank { "复制失败，请稍后重试" }
                }
            }
        }
    }

    fun toggleAttachmentPanel() {
        host.voiceMode = false
        abortVoiceRecording()
        host.attachmentPanelVisible = !host.attachmentPanelVisible
        if (host.attachmentPanelVisible) {
            host.keyboardHeight = 0f
            host.composerFocused = false
            dismissKeyboard()
        }
        syncAccessoryBackHandler()
    }

    fun selectAttachment(type: String) {
        val module = runtime.attachmentModule()
        val callback: (JSONObject?) -> Unit = { result: JSONObject? ->
            runtime.after(0) {
                if (result?.optBoolean("cancelled", false) == true) return@after
                val attachments = result?.optJSONArray("attachments")?.let(::decodeAttachments).orEmpty()
                if (attachments.isNotEmpty()) {
                    vm.addPendingAttachments(attachments)
                    vm.banner = "已添加${attachments.size}个附件，可继续编辑后发送"
                }
                host.attachmentPanelVisible = false
                host.voiceMode = false
                syncAccessoryBackHandler()
            }
            Unit
        }
        when (type) {
            "拍照" -> module.openCamera(callback)
            "照片" -> module.openPhotoLibrary(callback)
            else -> module.openFilePicker(callback)
        }
    }

    fun verifyAttachmentFiles(attachments: List<Attachment>, done: (Boolean) -> Unit) {
        val paths = attachments.map { it.localPath }.filter { it.isNotBlank() }
        if (paths.isEmpty()) {
            done(true)
            return
        }
        val module = runtime.attachmentModule()
        fun next(index: Int) {
            if (index >= paths.size) {
                done(true)
                return
            }
            module.fileExists(paths[index]) { json ->
                runtime.after(0) {
                    if (json?.optBoolean("exists", false) != true) done(false)
                    else next(index + 1)
                }
            }
        }
        next(0)
    }

    fun toggleVoiceMode() {
        host.attachmentPanelVisible = false
        abortVoiceRecording()
        host.voiceMode = !host.voiceMode
        if (host.voiceMode) {
            dismissKeyboard()
            vm.banner = ""
        }
        syncAccessoryBackHandler()
    }

    fun resetVoiceRecording() {
        host.voiceRecording = false
        host.voiceZone = VoiceRecordZone.SEND
        ++voiceWaveGeneration
    }

    private fun abortVoiceRecording() {
        ++voiceStartGeneration
        voiceGestureActive = false
        runtime.voiceModule.cancelListening()
        resetVoiceRecording()
    }

    fun handleVoiceLongPress(params: LongPressParams) {
        when (params.state) {
            "start" -> {
                if (!host.voiceMode || host.voiceRecording) return
                val generation = ++voiceStartGeneration
                voiceGestureActive = true
                host.voiceFingerX = params.pageX
                host.voiceFingerY = params.pageY
                host.voiceZone = VoiceRecordZone.SEND
                runtime.voiceModule.startListening { result ->
                    runtime.after(0) {
                        if (generation != voiceStartGeneration || !voiceGestureActive) {
                            if (result.success && generation == voiceStartGeneration) {
                                runtime.voiceModule.cancelListening()
                                vm.banner = "语音能力已就绪，请重新按住说话"
                            }
                            return@after
                        }
                        if (!result.success) {
                            vm.banner = result.message
                            return@after
                        }
                        host.voiceRecording = true
                        host.voiceZone = VoiceRecordZone.SEND
                        host.voiceFingerX = params.pageX
                        host.voiceFingerY = params.pageY
                        vm.banner = ""
                        startVoiceWave()
                        syncAccessoryBackHandler()
                    }
                }
            }
            "move" -> if (voiceGestureActive) {
                host.voiceFingerX = params.pageX
                host.voiceFingerY = params.pageY
                host.voiceZone = voiceRecordZone(
                    params.pageX,
                    params.pageY,
                    runtime.pageWidth(),
                    runtime.pageHeight(),
                    runtime.bottomInset(),
                )
            }
            "end" -> {
                voiceGestureActive = false
                if (!host.voiceRecording) return
                val zone = if (params.isCancel) VoiceRecordZone.CANCEL else host.voiceZone
                resetVoiceRecording()
                syncAccessoryBackHandler()
                when (zone) {
                    VoiceRecordZone.CANCEL -> {
                        runtime.voiceModule.cancelListening()
                        vm.banner = "已取消语音输入"
                    }
                    VoiceRecordZone.EDIT -> finishVoiceToComposer()
                    VoiceRecordZone.SEND -> finishVoiceToSend()
                }
            }
        }
    }

    private fun finishVoiceToSend() {
        vm.banner = "正在识别语音…"
        runtime.voiceModule.finishListening { result ->
            runtime.after(0) {
                val text = result.text
                if (!text.isNullOrBlank()) {
                    vm.banner = ""
                    sendPrompt(text)
                } else {
                    vm.banner = result.error ?: "未识别到有效语音"
                }
            }
        }
    }

    private fun finishVoiceToComposer() {
        vm.banner = "正在识别语音…"
        runtime.voiceModule.finishListening { result ->
            runtime.after(0) {
                val text = result.text
                if (!text.isNullOrBlank()) {
                    vm.banner = ""
                    host.voiceMode = false
                    vm.inputText = text
                    host.inputRef?.view?.setText(text)
                    host.inputRef?.view?.focus()
                    syncAccessoryBackHandler()
                } else {
                    vm.banner = result.error ?: "未识别到有效语音"
                }
            }
        }
    }

    private fun startVoiceWave() {
        val generation = ++voiceWaveGeneration
        fun tick() {
            if (!host.voiceRecording || generation != voiceWaveGeneration) return
            host.voiceWavePhase = (host.voiceWavePhase + 1) % 360
            runtime.after(48) { tick() }
        }
        runtime.after(48) { tick() }
    }

    fun syncAccessoryBackHandler() {
        if (host.attachmentPanelVisible || host.voiceMode || host.voiceRecording) {
            if (!runtime.containsBackCallback(accessoryBackCallback)) {
                runtime.addBackCallback(accessoryBackCallback)
            }
        } else {
            runtime.removeBackCallback(accessoryBackCallback)
        }
    }

    fun handleHomeSwipe(params: PanGestureParams) = handleDrawerPan(params, PAN_SOURCE_HOME)
    fun handleCloseSwipe(params: PanGestureParams) = handleDrawerPan(params, PAN_SOURCE_CLOSE)

    private fun handleDrawerPan(params: PanGestureParams, source: Int) {
        when (params.state) {
            "start" -> {
                if (host.panSource != 0) return
                if (source == PAN_SOURCE_HOME && drawer.progress > 0f) return
                panResumeTarget = targetProgress
                ++motionGeneration
                drawer.settling = false
                host.panSource = source
                panStartX = params.pageX
                panStartY = params.pageY
                panStartProgress = drawer.progress
                panLastX = params.pageX
                panLastTime = runtime.now()
                panVelocityX = 0f
                panLocked = false
                host.suppressDrawerClick = false
                ++clickGeneration
            }
            "move" -> {
                if (host.panSource != source) return
                val dx = params.pageX - panStartX
                val dy = params.pageY - panStartY
                if (!panLocked) {
                    if (maxOf(abs(dx), abs(dy)) < 8f) return
                    if (abs(dy) >= abs(dx)) {
                        settleDrawer(panResumeTarget)
                        return
                    }
                    if (source == PAN_SOURCE_HOME && dx <= 0f) return
                    panLocked = true
                    drawer.dragging = true
                    host.suppressDrawerClick = true
                    dismissKeyboard()
                    syncDrawerBackHandler()
                }
                val now = runtime.now()
                val dt = now - panLastTime
                if (dt > 0) {
                    panVelocityX = (params.pageX - panLastX) / dt * 1000f
                    panLastX = params.pageX
                    panLastTime = now
                }
                drawer.progress = DrawerPhysics.progress(
                    panStartProgress, dx, chatDrawerWidth(runtime.pageWidth()),
                )
            }
            "end", "cancel" -> {
                if (host.panSource != source) return
                val locked = panLocked
                val velocity = if (runtime.now() - panLastTime > 100L) 0f else panVelocityX
                val target = if (params.state == "cancel" || !locked) panResumeTarget
                    else DrawerPhysics.target(drawer.progress, velocity)
                panLocked = false
                settleDrawer(target)
                if (locked) {
                    val token = ++clickGeneration
                    runtime.after(100) { if (token == clickGeneration) host.suppressDrawerClick = false }
                }
            }
        }
    }

    fun scheduleScrollToBottom() {
        runtime.after(60) { scrollToBottom(animated = true) }
    }

    fun scrollToBottom(animated: Boolean) {
        val list = host.listRef?.view ?: return
        val listHeight = list.flexNode.layoutFrame.height
        val target = host.listContentHeight - listHeight
        if (target > 0) list.setContentOffset(0f, target, animated)
    }

    fun cancelMotions() {
        ++motionGeneration
        ++clickGeneration
    }

    fun detachBackHandlers() {
        runtime.removeBackCallback(drawerBackCallback)
        runtime.removeBackCallback(accessoryBackCallback)
    }

    private fun decodeAttachments(array: JSONArray): List<Attachment> =
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { json ->
                val kind = if (json.optString("kind") == "IMAGE") AttachmentKind.IMAGE else AttachmentKind.DOCUMENT
                val source = when (json.optString("source")) {
                    "CAMERA" -> AttachmentSource.CAMERA
                    "PHOTO_LIBRARY" -> AttachmentSource.PHOTO_LIBRARY
                    else -> AttachmentSource.FILE
                }
                Attachment(
                    id = json.optString("id"), displayName = json.optString("displayName"),
                    mimeType = json.optString("mimeType"), byteSize = json.optLong("byteSize"),
                    localPath = json.optString("localPath"), thumbnailPath = json.optString("thumbnailPath").ifEmpty { null },
                    source = source, kind = kind,
                    status = AttachmentStatus.READY,
                )
            }
        }
}
