package dev.jamesnicholls.memoswidget.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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

data class MemoSummary(
    val name: String,
    val content: String,
    val createTime: String?,
)

data class ConnectionInfo(
    val serverVersion: String?,
    val username: String?,
    val displayName: String?,
)

data class CreatedMemo(
    val name: String,
)

data class UploadedAttachment(
    val name: String,
    val filename: String,
)

@kotlinx.serialization.Serializable
private data class AttachmentRef(val name: String)

@kotlinx.serialization.Serializable
private data class CreateMemoRequest(
    val content: String,
    val visibility: String,
    val attachments: List<AttachmentRef> = emptyList(),
)

class MemosApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun createMemo(
        baseUrl: String,
        accessToken: String,
        content: String,
        visibilityWireName: String,
        attachmentNames: List<String> = emptyList(),
    ): CreatedMemo = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            CreateMemoRequest(
                content = content,
                visibility = visibilityWireName,
                attachments = attachmentNames.map { AttachmentRef(it) },
            ),
        ).toRequestBody("application/json".toMediaType())
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

    /**
     * Uploads a file via the Memos 0.31 chunked attachment protocol
     * (POST /api/v1/attachments:upload). The first call carries the spec and
     * returns an uploadId plus maxChunkSize; subsequent calls stream bounded
     * base64 chunks and finish the upload.
     */
    suspend fun uploadAttachment(
        baseUrl: String,
        accessToken: String,
        filename: String,
        mimeType: String,
        totalSize: Long,
        openStream: () -> java.io.InputStream,
    ): UploadedAttachment = withContext(Dispatchers.IO) {
        openStream().use { stream ->
            // 1. Start the upload: spec only, no data, to learn maxChunkSize.
            val initJson = kotlinx.serialization.json.buildJsonObject {
                put(
                    "spec",
                    kotlinx.serialization.json.buildJsonObject {
                        put("filename", kotlinx.serialization.json.JsonPrimitive(filename))
                        put("type", kotlinx.serialization.json.JsonPrimitive(mimeType))
                        put("totalSize", kotlinx.serialization.json.JsonPrimitive(totalSize.toString()))
                    },
                )
                put("writeOffset", kotlinx.serialization.json.JsonPrimitive("0"))
            }
            val initResp = postUpload(baseUrl, accessToken, initJson.toString())
            val uploadId = initResp.stringOrNull("uploadId")
                ?: throw MemosApiException(MemosApiException.Kind.PARSE, "Upload start did not return an uploadId")
            val maxChunk = initResp["maxChunkSize"]?.jsonPrimitive?.content?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_CHUNK_BYTES

            var committed = initResp["committedSize"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            initResp["attachment"]?.jsonObject?.let { return@withContext it.toUploadedAttachment() }

            val buffer = ByteArray(maxChunk)
            while (committed < totalSize) {
                val toRead = minOf(maxChunk.toLong(), totalSize - committed).toInt()
                var read = 0
                while (read < toRead) {
                    val n = stream.read(buffer, read, toRead - read)
                    if (n < 0) throw MemosApiException(
                        MemosApiException.Kind.PARSE,
                        "Attachment stream ended before the declared size",
                    )
                    read += n
                }
                val finish = committed + read >= totalSize
                val chunkJson = kotlinx.serialization.json.buildJsonObject {
                    put("uploadId", kotlinx.serialization.json.JsonPrimitive(uploadId))
                    put("writeOffset", kotlinx.serialization.json.JsonPrimitive(committed.toString()))
                    put("data", kotlinx.serialization.json.JsonPrimitive(java.util.Base64.getEncoder().encodeToString(buffer.copyOf(read))))
                    put("finishWrite", kotlinx.serialization.json.JsonPrimitive(finish))
                }
                val resp = postUpload(baseUrl, accessToken, chunkJson.toString())
                committed = resp["committedSize"]?.jsonPrimitive?.content?.toLongOrNull() ?: (committed + read)
                resp["attachment"]?.jsonObject?.let { return@withContext it.toUploadedAttachment() }
            }

            // Some servers only materialise the attachment on a follow-up status call.
            val statusJson = kotlinx.serialization.json.buildJsonObject {
                put("uploadId", kotlinx.serialization.json.JsonPrimitive(uploadId))
                put("writeOffset", kotlinx.serialization.json.JsonPrimitive(committed.toString()))
            }
            val resp = postUpload(baseUrl, accessToken, statusJson.toString())
            resp["attachment"]?.jsonObject?.let { return@withContext it.toUploadedAttachment() }
            throw MemosApiException(MemosApiException.Kind.PARSE, "Upload finished but no attachment was returned")
        }
    }

    private fun postUpload(baseUrl: String, accessToken: String, bodyJson: String): kotlinx.serialization.json.JsonObject {
        val request = Request.Builder()
            .url("${baseUrl}/api/v1/attachments:upload")
            .header("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        return executeForJson(request).jsonObject
    }

    private fun kotlinx.serialization.json.JsonObject.toUploadedAttachment() = UploadedAttachment(
        name = stringOrNull("name")
            ?: throw MemosApiException(MemosApiException.Kind.PARSE, "Attachment response did not include a name"),
        filename = stringOrNull("filename").orEmpty(),
    )

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
     * Fetches the user's most recently created memos (newest first).
     */
    suspend fun listRecentMemos(
        baseUrl: String,
        accessToken: String,
        limit: Int = 3,
    ): List<MemoSummary> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}/api/v1/memos?pageSize=$limit")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        val element = executeForJson(request)
        val memos = element.jsonObject["memos"]?.jsonArray ?: return@withContext emptyList()
        memos.mapNotNull { entry ->
            val obj = entry.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            MemoSummary(
                name = name,
                content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                createTime = obj["createTime"]?.jsonPrimitive?.contentOrNull,
            )
        }
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
        private const val DEFAULT_CHUNK_BYTES = 512 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
