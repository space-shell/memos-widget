package dev.jamesnicholls.memoswidget.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.jamesnicholls.memoswidget.MainActivity
import dev.jamesnicholls.memoswidget.MemosApp
import dev.jamesnicholls.memoswidget.net.UrlUtil
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sends a memo with attachments in the background: uploads each cached file
 * via the chunked attachment protocol, then creates the memo with them bound.
 */
class SendMemoWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Serializable
    data class PendingAttachment(
        val path: String,
        val filename: String,
        val mimeType: String,
    )

    override suspend fun doWork(): Result {
        val container = (applicationContext as MemosApp).container
        val content = inputData.getString(KEY_CONTENT).orEmpty()
        val visibility = inputData.getString(KEY_VISIBILITY).orEmpty()
        val attachmentsJson = inputData.getString(KEY_ATTACHMENTS)
        val attachments = attachmentsJson?.let {
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<List<PendingAttachment>>(it) }
                .getOrDefault(emptyList())
        }.orEmpty()

        val settings = container.settingsRepository.settings.first()
        if (!settings.isConfigured) {
            notifyFailure("Server is not configured")
            return Result.failure()
        }

        return try {
            val baseUrl = UrlUtil.normaliseBaseUrl(settings.serverUrl)
            val uploaded = attachments.map { pending ->
                val file = File(pending.path)
                container.memosApi.uploadAttachment(
                    baseUrl = baseUrl,
                    accessToken = settings.accessToken,
                    filename = pending.filename,
                    mimeType = pending.mimeType,
                    totalSize = file.length(),
                    openStream = { file.inputStream() },
                )
            }
            val created = container.memosApi.createMemo(
                baseUrl = baseUrl,
                accessToken = settings.accessToken,
                content = content,
                visibilityWireName = visibility,
                attachmentNames = uploaded.map { it.name },
            )
            attachments.forEach { File(it.path).delete() }
            container.widgetRefresher.onMemoSent(created.name, content)
            container.widgetRefresher.requestRefresh()
            notifySuccess()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("MemosWidget", "attachment send failed (attempt $runAttemptCount): ${e.message}", e)
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                notifyFailure(e.message ?: "Unknown error")
                Result.failure()
            }
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        REQUEST_CONTENT,
        Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notifySuccess() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_SEND)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle(applicationContext.getString(dev.jamesnicholls.memoswidget.R.string.compose_sent))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFY_SUCCESS, notification)
    }

    private fun notifyFailure(reason: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_SEND)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Memo failed to send")
            .setContentText(reason)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFY_FAILURE, notification)
    }

    companion object {
        const val CHANNEL_SEND = "send"
        private const val UNIQUE_SEND = "send-memo"
        private const val KEY_CONTENT = "content"
        private const val KEY_VISIBILITY = "visibility"
        private const val KEY_ATTACHMENTS = "attachments"
        private const val MAX_ATTEMPTS = 3
        private const val REQUEST_CONTENT = 3001
        private const val NOTIFY_SUCCESS = 4001
        private const val NOTIFY_FAILURE = 4002

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_SEND,
                "Sending memos",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background attachment uploads and send results"
            }
            manager.createNotificationChannel(channel)
        }

        fun enqueue(context: Context, content: String, visibilityWireName: String, attachments: List<PendingAttachment>) {
            val json = Json { ignoreUnknownKeys = true }
            val request = OneTimeWorkRequestBuilder<SendMemoWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_CONTENT, content)
                        .putString(KEY_VISIBILITY, visibilityWireName)
                        .putString(KEY_ATTACHMENTS, json.encodeToString(attachments))
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_SEND, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
