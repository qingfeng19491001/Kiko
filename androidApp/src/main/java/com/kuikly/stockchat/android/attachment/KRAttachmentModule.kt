package com.kuikly.stockchat.android.attachment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID

class KRAttachmentModule : KuiklyRenderBaseModule() {
    private var callback: KuiklyRenderCallback? = null
    private var pendingCameraFile: File? = null

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        when (method) {
            "open" -> {
                this.callback = callback
                when (JSONObject(params ?: "{}").optString("source")) {
                    "camera" -> launchCamera()
                    "photo_library" -> launchPicker("image/*", allowMultiple = true)
                    else -> launchPicker("*/*", allowMultiple = true)
                }
            }
            "readFile" -> readFile(JSONObject(params ?: "{}").optString("path"), callback)
            else -> return super.call(method, params, callback)
        }
        return null
    }

    private fun readFile(path: String, callback: KuiklyRenderCallback?) {
        val result = JSONObject()
        try {
            val file = File(path)
            if (!file.isFile) {
                callback?.invoke(result.put("success", false).put("error", "文件不存在").toString())
                return
            }
            if (file.length() > MAX_READ_BYTES) {
                callback?.invoke(result.put("success", false).put("error", "附件过大，请换一张较小的图片").toString())
                return
            }
            val bytes = file.readBytes()
            val mime = mimeOf(file.name)
            if (mime.startsWith("image/")) {
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                result.put("success", true).put("dataUrl", "data:$mime;base64,$encoded")
            } else if (mime.startsWith("text/") || mime == "application/json") {
                result.put("success", true).put("text", String(bytes, Charset.forName("UTF-8")))
            } else {
                result.put("success", true)
            }
        } catch (error: Exception) {
            result.put("success", false).put("error", error.message ?: "读取附件失败")
        }
        callback?.invoke(result.toString())
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "txt", "md", "csv" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = if (resultCode != Activity.RESULT_OK) {
            JSONObject().put("cancelled", true)
        } else if (requestCode == REQUEST_CAMERA) {
            pendingCameraFile?.let { file ->
                (data?.extras?.get("data") as? android.graphics.Bitmap)?.let { bitmap ->
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
                JSONObject().put("cancelled", false).put("attachments", JSONArray().put(metadata(file, "image/png", "拍摄图片.png")))
            } ?: JSONObject().put("cancelled", false).put("attachments", JSONArray())
        } else {
            val uris = buildList {
                data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
                data?.data?.let(::add)
            }
            JSONObject().put("cancelled", false).put("attachments", JSONArray().apply {
                uris.mapNotNull(::copyUri).forEach { put(it) }
            })
        }
        callback?.invoke(result.toString())
        callback = null
        pendingCameraFile = null
    }

    private fun launchCamera() {
        val host = activity ?: return
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        pendingCameraFile = File(host.cacheDir, "attachment-${UUID.randomUUID()}.png")
        host.startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun launchPicker(type: String, allowMultiple: Boolean) {
        val host = activity ?: return
        host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        }, REQUEST_PICKER)
    }

    private fun copyUri(uri: Uri): JSONObject? {
        val host = activity ?: return null
        val resolver = host.contentResolver
        val name = resolver.query(uri, arrayOf("_display_name", "_size"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "附件"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val file = File(host.filesDir, "attachments/${UUID.randomUUID()}-$name").also { it.parentFile?.mkdirs() }
        resolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { input.copyTo(it) } } ?: return null
        return metadata(file, mime, name)
    }

    private fun metadata(file: File, mime: String, displayName: String): JSONObject = JSONObject().apply {
        put("id", file.nameWithoutExtension)
        put("displayName", displayName)
        put("mimeType", mime)
        put("byteSize", file.length())
        put("localPath", file.absolutePath)
        put("source", if (mime.startsWith("image/")) "PHOTO_LIBRARY" else "FILE")
        put("kind", if (mime.startsWith("image/")) "IMAGE" else "DOCUMENT")
        put("status", "READY")
    }

    companion object {
        const val MODULE_NAME = "KRAttachmentModule"
        const val REQUEST_CAMERA = 7201
        const val REQUEST_PICKER = 7202
        private const val MAX_READ_BYTES = 4L * 1024 * 1024
    }
}
