package com.kuikly.stockchat.data.ai

import com.kuikly.stockchat.data.attachment.FileUploader
import com.kuikly.stockchat.data.attachment.LoadedAttachment
import com.kuikly.stockchat.data.network.HttpClient
import com.kuikly.stockchat.domain.attachment.Attachment
import com.kuikly.stockchat.domain.attachment.AttachmentKind
import com.kuikly.stockchat.domain.attachment.AttachmentSource
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashScopeDocumentClientTest {
    @Test
    fun parseFileIdReadsDashScopeResponse() {
        assertEquals("file-fe-123", DashScopeDocumentClient.parseFileId("""{"id":"file-fe-123","object":"file"}"""))
        assertNull(DashScopeDocumentClient.parseFileId("""{"object":"file"}"""))
    }

    @Test
    fun enrichUploadsDocumentAndFillsExcerpt() {
        val uploader = FakeUploader(body = """{"id":"file-fe-9"}""")
        val http = FakeHttp(
            """{"choices":[{"message":{"content":"年报摘要正文"}}]}""",
        )
        val client = DashScopeDocumentClient(http, uploader, apiKey = "sk-test", baseUrl = "https://example.com")
        var enriched: List<LoadedAttachment> = emptyList()
        var ids: List<String> = emptyList()
        client.enrich(listOf(LoadedAttachment(sampleDocument()))) { loaded, remoteIds ->
            enriched = loaded
            ids = remoteIds
        }
        assertEquals(listOf("file-fe-9"), ids)
        assertEquals("年报摘要正文", enriched.single().textExcerpt)
        assertEquals("/tmp/report.pdf", uploader.uploadedPath)
        assertTrue(http.postedUrl.endsWith("/chat/completions"))
    }

    @Test
    fun enrichSkipsImagesAndPlainTextDocuments() {
        val uploader = FakeUploader(body = """{"id":"file-fe-x"}""")
        val http = FakeHttp("{}")
        val client = DashScopeDocumentClient(http, uploader, apiKey = "sk-test")
        val image = LoadedAttachment(sampleDocument().copy(kind = AttachmentKind.IMAGE, mimeType = "image/png"), dataUrl = "data:image/png;base64,a")
        val textDoc = LoadedAttachment(sampleDocument().copy(displayName = "notes.txt"), textExcerpt = "already")
        var called = false
        client.enrich(listOf(image, textDoc)) { loaded, remoteIds ->
            called = true
            assertTrue(remoteIds.isEmpty())
            assertEquals(2, loaded.size)
        }
        assertTrue(called)
        assertEquals("", uploader.uploadedPath)
    }

    private fun sampleDocument() = Attachment(
        id = "d1",
        displayName = "report.pdf",
        mimeType = "application/pdf",
        byteSize = 20,
        localPath = "/tmp/report.pdf",
        source = AttachmentSource.FILE,
        kind = AttachmentKind.DOCUMENT,
    )
}

private class FakeUploader(private val body: String) : FileUploader {
    var uploadedPath: String = ""
    override fun upload(
        path: String,
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        callback: (String?, String?) -> Unit,
    ) {
        uploadedPath = path
        callback(body, null)
    }

    override fun delete(url: String, headers: Map<String, String>, callback: (Boolean) -> Unit) {
        callback(true)
    }
}

private class FakeHttp(private val response: String) : HttpClient {
    var postedUrl: String = ""
    override fun get(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String>,
        callback: (String?, String?) -> Unit,
    ) = callback(null, "unused")

    override fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String>,
        timeoutSeconds: Int,
        callback: (String?, String?) -> Unit,
    ) {
        postedUrl = url
        callback(response, null)
    }
}
