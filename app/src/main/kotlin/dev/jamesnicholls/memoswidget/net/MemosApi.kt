package dev.jamesnicholls.memoswidget.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class MemosApiException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { AUTH, SERVER, NETWORK, PARSE }
}

data class InstanceProfile(
    val version: String?,
    val instanceUrl: String?,
)

data class CurrentUser(
    val username: String?,
    val displayName: String?,
    val email: String?,
)

data class ConnectionInfo(
    val serverVersion: String?,
    val username: String?,
    val displayName: String?,
)

data class CreatedMemo(
    val name: String,
)

@kotlinx.serialization.Serializable
private data class CreateMemoRequest(val content: String, val visibility: String)

class MemosApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun createMemo(
        baseUrl: String,
        accessToken: String,
        content: String,
        visibilityWireName: String,
    ): CreatedMemo = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CreateMemoRequest(content, visibilityWireName))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl}/api/v1/memos")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        val element = executeForJson(request)
        val name = element.jsonObject["name"]?.jsonPrimitive?.content
            ?: throw MemosApiException(MemosApiException.Kind.PARSE, "Response did not include a memo name")
        CreatedMemo(name = name)
    }

    suspend fun getInstanceProfile(baseUrl: String): InstanceProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}/api/v1/instance/profile")
            .get()
            .build()
        val element = executeForJson(request)
        InstanceProfile(
            version = element.jsonObject.stringOrNull("version"),
            instanceUrl = element.jsonObject.stringOrNull("instanceUrl"),
        )
    }

    suspend fun getCurrentUser(baseUrl: String, accessToken: String): CurrentUser = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}/api/v1/auth/me")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        val element = executeForJson(request)
        val user = element.jsonObject["user"]?.jsonObject
            ?: throw MemosApiException(MemosApiException.Kind.PARSE, "Response did not include a user object")
        CurrentUser(
            username = user.stringOrNull("username"),
            displayName = user.stringOrNull("displayName"),
            email = user.stringOrNull("email"),
        )
    }

    /**
     * Verifies the server address is a reachable Memos instance and the token is valid.
     * Profile is fetched first (no auth) so failures distinguish a bad URL from a bad token.
     */
    suspend fun validateConnection(baseUrl: String, accessToken: String): ConnectionInfo {
        val profile = getInstanceProfile(baseUrl)
        val user = getCurrentUser(baseUrl, accessToken)
        return ConnectionInfo(
            serverVersion = profile.version,
            username = user.username,
            displayName = user.displayName,
        )
    }

    private fun executeForJson(request: Request): kotlinx.serialization.json.JsonElement {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw MemosApiException(
                MemosApiException.Kind.NETWORK,
                "Could not reach the server: ${e.message}",
                e,
            )
        }
        response.use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val apiMessage = parseErrorMessage(bodyText)
                val kind = when {
                    resp.code == 401 || resp.code == 403 -> MemosApiException.Kind.AUTH
                    apiMessage != null -> MemosApiException.Kind.SERVER
                    else -> MemosApiException.Kind.SERVER
                }
                throw MemosApiException(
                    kind,
                    apiMessage ?: "Server returned HTTP ${resp.code}",
                )
            }
            return try {
                json.parseToJsonElement(bodyText)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw MemosApiException(
                    MemosApiException.Kind.PARSE,
                    "Could not parse the server response",
                    e,
                )
            }
        }
    }

    private fun parseErrorMessage(bodyText: String): String? = try {
        val obj = json.parseToJsonElement(bodyText).jsonObject
        obj["message"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    private fun kotlinx.serialization.json.JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.content

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
