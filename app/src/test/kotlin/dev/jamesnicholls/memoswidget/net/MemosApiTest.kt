package dev.jamesnicholls.memoswidget.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MemosApiTest {

    private lateinit var server: MockWebServer
    private val api = MemosApi()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun `createMemo posts auth header and body to memos endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"name":"memos/abc123","content":"hi"}""")
        )

        val memo = api.createMemo(baseUrl(), "tok123", "hello #world", "PRIVATE")

        assertEquals("memos/abc123", memo.name)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/memos", recorded.path)
        assertEquals("Bearer tok123", recorded.getHeader("Authorization"))
        assertEquals("application/json", recorded.getHeader("Content-Type")!!.substringBefore(';'))
        val sent = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("hello #world", sent["content"]?.jsonPrimitive?.content)
        assertEquals("PRIVATE", sent["visibility"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createMemo maps 401 to AUTH error`() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"code":16,"message":"Permission denied"}""")
        )

        val error = runCatching {
            runBlocking { api.createMemo(baseUrl(), "bad", "hi", "PRIVATE") }
        }.exceptionOrNull()

        val apiError = error as? MemosApiException
        requireNotNull(apiError) { "expected MemosApiException, got $error" }
        assertEquals(MemosApiException.Kind.AUTH, apiError.kind)
        assertEquals("Permission denied", apiError.message)
    }

    @Test
    fun `createMemo surfaces grpc error message`() {
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody("""{"code":13,"message":"internal boom"}""")
        )

        val error = runCatching {
            runBlocking { api.createMemo(baseUrl(), "tok", "hi", "PRIVATE") }
        }.exceptionOrNull() as? MemosApiException

        requireNotNull(error)
        assertEquals(MemosApiException.Kind.SERVER, error.kind)
        assertEquals("internal boom", error.message)
    }

    @Test
    fun `validateConnection hits profile then auth me`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"version":"0.31.0","instanceUrl":"https://m.io"}""")
        )
        server.enqueue(
            MockResponse().setBody("""{"user":{"username":"james","displayName":"James"}}""")
        )

        val info = api.validateConnection(baseUrl(), "tok")

        assertEquals("0.31.0", info.serverVersion)
        assertEquals("james", info.username)
        assertEquals("James", info.displayName)

        val profileRequest = server.takeRequest()
        assertEquals("/api/v1/instance/profile", profileRequest.path)
        assertNull(profileRequest.getHeader("Authorization"))

        val meRequest = server.takeRequest()
        assertEquals("/api/v1/auth/me", meRequest.path)
        assertEquals("Bearer tok", meRequest.getHeader("Authorization"))
    }

    @Test
    fun `listRecentMemos fetches latest memos with auth`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {"memos":[
                  {"name":"memos/c","content":"third #journal","createTime":"2026-09-14T10:00:00Z"},
                  {"name":"memos/b","content":"second","createTime":"2026-09-13T09:00:00Z"},
                  {"name":"memos/a","content":"first","createTime":"2026-09-12T08:00:00Z"}
                ]}
                """.trimIndent()
            )
        )

        val memos = api.listRecentMemos(baseUrl(), "tok", limit = 3)

        assertEquals(3, memos.size)
        assertEquals("memos/c", memos[0].name)
        assertEquals("third #journal", memos[0].content)
        assertEquals("2026-09-14T10:00:00Z", memos[0].createTime)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/memos?pageSize=3", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
    }

    @Test
    fun `listRecentMemos handles empty list`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"memos":[]}"""))

        val memos = api.listRecentMemos(baseUrl(), "tok")

        assertEquals(0, memos.size)
    }

    @Test
    fun `createMemo includes attachment references`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"name":"memos/withatt","content":"hi"}""")
        )

        val memo = api.createMemo(
            baseUrl(), "tok123", "see this",
            "PRIVATE",
            attachmentNames = listOf("attachments/abc", "attachments/def"),
        )

        assertEquals("memos/withatt", memo.name)
        val sent = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val attachments = sent["attachments"]?.jsonArray
        requireNotNull(attachments)
        assertEquals(2, attachments.size)
        assertEquals("attachments/abc", attachments[0].jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `uploadAttachment streams chunked protocol`() = runBlocking {
        val payload = ByteArray(5) { ('a'.code + it).toByte() } // abcde
        val chunk1 = java.util.Base64.getEncoder().encodeToString(payload.copyOfRange(0, 3))
        val chunk2 = java.util.Base64.getEncoder().encodeToString(payload.copyOfRange(3, 5))

        server.enqueue(
            MockResponse().setBody(
                """{"uploadId":"u1","committedSize":"0","maxChunkSize":3}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"uploadId":"u1","committedSize":"3"}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"uploadId":"u1","committedSize":"5","attachment":{"name":"attachments/up1","filename":"a.txt"}}"""
            )
        )

        val uploaded = api.uploadAttachment(
            baseUrl(), "tok123", "a.txt", "text/plain", payload.size.toLong(),
        ) { java.io.ByteArrayInputStream(payload) }

        assertEquals("attachments/up1", uploaded.name)
        assertEquals("a.txt", uploaded.filename)

        val initRequest = server.takeRequest()
        assertEquals("/api/v1/attachments:upload", initRequest.path)
        val init = json.parseToJsonElement(initRequest.body.readUtf8()).jsonObject
        val initSpec = init["spec"]?.jsonObject
        requireNotNull(initSpec)
        assertEquals("a.txt", initSpec["filename"]?.jsonPrimitive?.content)
        assertEquals("text/plain", initSpec["type"]?.jsonPrimitive?.content)
        assertEquals("5", initSpec["totalSize"]?.jsonPrimitive?.content)
        assertEquals("0", init["writeOffset"]?.jsonPrimitive?.content)

        val first = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("u1", first["uploadId"]?.jsonPrimitive?.content)
        assertEquals("0", first["writeOffset"]?.jsonPrimitive?.content)
        assertEquals(chunk1, first["data"]?.jsonPrimitive?.content)
        assertEquals(false, first["finishWrite"]?.jsonPrimitive?.booleanOrNull)

        val last = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("3", last["writeOffset"]?.jsonPrimitive?.content)
        assertEquals(chunk2, last["data"]?.jsonPrimitive?.content)
        assertEquals(true, last["finishWrite"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `network failure maps to NETWORK error`() {
        server.shutdown()

        val error = runCatching {
            runBlocking { api.createMemo(baseUrl(), "tok", "hi", "PRIVATE") }
        }.exceptionOrNull() as? MemosApiException

        requireNotNull(error)
        assertEquals(MemosApiException.Kind.NETWORK, error.kind)
    }
}
