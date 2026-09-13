package com.kuikly.stockchat.android.attachment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID

class KRAttachmentModule : KuiklyRenderBaseModule() {
    private var callback: KuiklyRenderCallback? = null
    private var pendingCameraFile: File? = null
    private var pendingSource: String = "file"
    private var pendingPermissionAction: String? = null

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        val json = JSONObject(params ?: "{}")
        when (method) {
            "open" -> {
                this.callback = callback
                when (json.optString("source")) {
                    "camera" -> launchCamera()
                    "photo_library" -> launchPicker("image/*", "PHOTO_LIBRARY")
                    else -> launchPicker("*/*", "FILE")
                }
            }
            "readFile" -> readFile(json.optString("path"), callback)
            "deleteFile" -> {
                val ok = File(json.optString("path")).takeIf { it.isFile }?.delete() == true
                callback?.invoke(JSONObject().put("success", ok).toString())
            }
            "fileExists" -> {
                val exists = File(json.optString("path")).isFile
                callback?.invoke(JSONObject().put("exists", exists).toString())
            }
            "upload" -> upload(json, callback)
            "deleteRemote" -> deleteRemote(json, callback)
            else -> return super.call(method, params, callback)
        }
        return null
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_CAMERA_PERMISSION) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        pendingPermissionAction = null
        if (granted) launchCamera()
        else complete(JSONObject().put("cancelled", false).put("error", "未获得相机权限"))
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = if (resultCode != Activity.RESULT_OK) {
            pendingCameraFile?.delete()
            JSONObject().put("cancelled", true)
        } else if (requestCode == REQUEST_CAMERA) {
            val file = pendingCameraFile
            if (file == null || !file.isFile || file.length() == 0L) {
                JSONObject().put("cancelled", false).put("attachments", JSONArray())
            } else {
                JSONObject().put("cancelled", false).put(
                    "attachments",
                    JSONArray().put(metadata(file, "image/jpeg", "拍摄图片.jpg", "CAMERA")),
                )
            }
        } else {
            val uris = buildList {
                data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
                data?.data?.let(::add)
            }
            JSONObject().put("cancelled", false).put("attachments", JSONArray().apply {
                uris.mapNotNull { copyUri(it, pendingSource) }.forEach { put(it) }
            })
        }
        complete(result)
    }

    private fun launchCamera() {
        val host = activity ?: return complete(JSONObject().put("cancelled", false).put("error", "当前页面无法打开相机"))
        if (ContextCompat.checkSelfPermission(host, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = "camera"
            host.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }
        pendingSource = "CAMERA"
        val file = File(attachmentsDir(), "cam-${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(host, "${host.packageName}.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        host.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { info ->
            host.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        runCatching { host.startActivityForResult(intent, REQUEST_CAMERA) }
            .onFailure { complete(JSONObject().put("cancelled", false).put("error", it.message ?: "无法打开相机")) }
    }

    private fun launchPicker(type: String, source: String) {
        val host = activity ?: return
        pendingSource = source
        pendingCameraFile = null
        host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, REQUEST_PICKER)
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

    private fun upload(params: JSONObject, callback: KuiklyRenderCallback?) {
        Thread {
            val result = runCatching {
                val file = File(params.optString("path"))
                require(file.isFile) { "文件不存在" }
                val boundary = "----StockChat${UUID.randomUUID()}"
                val conn = (URL(params.optString("url")).openConnection() as HttpURLConnection).apply {
                    doOutput = true
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    params.optJSONObject("headers")?.let { headers ->
                        headers.keys().forEach { key -> setRequestProperty(key, headers.optString(key)) }
                    }
                }
                conn.outputStream.use { out ->
                    fun write(value: String) = out.write(value.toByteArray())
                    params.optJSONObject("fields")?.let { fields ->
                        fields.keys().forEach { key ->
                            write("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n${fields.optString(key)}\r\n")
                        }
                    }
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    file.inputStream().use { it.copyTo(out) }
                    write("\r\n--$boundary--\r\n")
                }
                val ok = conn.responseCode in 200..299
                val stream = if (ok) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.readText().orEmpty()
                JSONObject().put("success", ok).put("body", body).put("error", if (ok) "" else body.take(240).ifBlank { "上传失败 ${conn.responseCode}" })
            }.getOrElse { JSONObject().put("success", false).put("error", it.message ?: "上传失败") }
            activity?.runOnUiThread { callback?.invoke(result.toString()) }
        }.start()
    }

    private fun deleteRemote(params: JSONObject, callback: KuiklyRenderCallback?) {
        Thread {
            val result = runCatching {
                val conn = (URL(params.optString("url")).openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    params.optJSONObject("headers")?.let { headers ->
                        headers.keys().forEach { key -> setRequestProperty(key, headers.optString(key)) }
                    }
                }
                val ok = conn.responseCode in 200..299
                JSONObject().put("success", ok)
            }.getOrElse { JSONObject().put("success", false).put("error", it.message ?: "删除失败") }
            activity?.runOnUiThread { callback?.invoke(result.toString()) }
        }.start()
    }

    private fun copyUri(uri: Uri, source: String): JSONObject? {
        val host = activity ?: return null
        val resolver = host.contentResolver
        val name = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "附件"
        val mime = resolver.getType(uri) ?: mimeOf(name)
        val file = File(attachmentsDir(), "${UUID.randomUUID()}-$name")
        resolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { input.copyTo(it) } } ?: return null
        return metadata(file, mime, name, source)
    }

    private fun metadata(file: File, mime: String, displayName: String, source: String): JSONObject = JSONObject().apply {
        val kind = if (mime.startsWith("image/")) "IMAGE" else "DOCUMENT"
        put("id", file.nameWithoutExtension)
        put("displayName", displayName)
        put("mimeType", mime)
        put("byteSize", file.length())
        put("localPath", file.absolutePath)
        put("source", source)
        put("kind", kind)
        put("status", "READY")
        if (kind == "IMAGE") writeThumbnail(file)?.let { put("thumbnailPath", it.absolutePath) }
    }

    private fun writeThumbnail(file: File): File? = runCatching {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        val scaled = ThumbnailUtils.extractThumbnail(bitmap, 256, 256)
        val thumb = File(file.parentFile, "${file.nameWithoutExtension}-thumb.jpg")
        FileOutputStream(thumb).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        thumb
    }.getOrNull()

    private fun attachmentsDir(): File = File(activity!!.filesDir, "attachments").also { it.mkdirs() }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "txt", "md", "csv" -> "text/plain"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    private fun complete(result: JSONObject) {
        callback?.invoke(result.toString())
        callback = null
        pendingCameraFile = null
        pendingSource = "file"
    }

    companion object {
        const val MODULE_NAME = "KRAttachmentModule"
        const val REQUEST_CAMERA = 7201
        const val REQUEST_PICKER = 7202
        const val REQUEST_CAMERA_PERMISSION = 7203
        private const val MAX_READ_BYTES = 12L * 1024 * 1024
    }
}
