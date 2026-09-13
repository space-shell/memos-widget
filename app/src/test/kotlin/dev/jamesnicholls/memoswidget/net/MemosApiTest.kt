package dev.jamesnicholls.memoswidget.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
    fun `network failure maps to NETWORK error`() {
        server.shutdown()

        val error = runCatching {
            runBlocking { api.createMemo(baseUrl(), "tok", "hi", "PRIVATE") }
        }.exceptionOrNull() as? MemosApiException

        requireNotNull(error)
        assertEquals(MemosApiException.Kind.NETWORK, error.kind)
    }
}
