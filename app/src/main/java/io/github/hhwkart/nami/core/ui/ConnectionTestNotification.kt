package io.github.hhwkart.nami.core.ui

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.bg.NotificationChannels
import io.github.hhwkart.nami.ktx.Logs

class ConnectionTestNotification(val context: Context, val title: String) {
    private val channelId = NotificationChannels.CONNECTION_TEST
    private val notificationId = 1001
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        NotificationChannels.ensure(context)
    }

    fun updateNotification(progress: Int, max: Int, finished: Boolean) {
        try {
            if (finished) {
                notificationManager.cancel(notificationId)
                return
            }
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification_wave)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setContentText("$progress / $max").setProgress(max, progress, false)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    fun cancel() {
        try {
            notificationManager.cancel(notificationId)
        } catch (e: Exception) {
            Logs.w(e)
        }
    }
}
