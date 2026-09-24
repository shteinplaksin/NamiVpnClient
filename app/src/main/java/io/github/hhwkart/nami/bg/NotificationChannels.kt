package io.github.hhwkart.nami.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.github.hhwkart.nami.R

/**
 * Owns the notification channel contract shared by the main and :bg processes.
 * createNotificationChannels is safe to call repeatedly and preserves the
 * user's existing channel settings when a channel already exists.
 */
object NotificationChannels {
    const val SERVICE_VPN = "service-vpn"
    const val SERVICE_PROXY = "service-proxy"
    const val SERVICE_SUBSCRIPTION = "service-subscription"
    const val CONNECTION_TEST = "connection-test"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as? NotificationManager ?: return

        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    SERVICE_VPN,
                    appContext.getText(R.string.service_vpn),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        NotificationManager.IMPORTANCE_MIN
                    } else {
                        NotificationManager.IMPORTANCE_LOW
                    },
                ),
                NotificationChannel(
                    SERVICE_PROXY,
                    appContext.getText(R.string.service_proxy),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    SERVICE_SUBSCRIPTION,
                    appContext.getText(R.string.service_subscription),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CONNECTION_TEST,
                    appContext.getText(R.string.connection_test),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }
}
