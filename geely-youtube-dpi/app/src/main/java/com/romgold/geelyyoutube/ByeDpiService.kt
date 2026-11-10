package com.romgold.geelyyoutube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy

/** Keeps the local SOCKS listener alive independently of Activity lifecycle. */
class ByeDpiService : Service() {
    private var proxy: ByeDpiProxy? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                proxy?.stopProxy()
                proxy = null
                isActive = false
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val storedArgs = getSharedPreferences("dpi_profiles", MODE_PRIVATE)
                    .getString("last_byedpi_args", "").orEmpty()
                val args = intent?.getStringExtra(EXTRA_ARGUMENTS) ?: storedArgs
                if (args.isBlank()) {
                    isActive = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                getSharedPreferences("dpi_profiles", MODE_PRIVATE).edit()
                    .putString("last_byedpi_args", args).apply()
                startForeground(NOTIFICATION_ID, buildNotification("Запуск локального SOCKS5…"))
                try {
                    if (proxy?.isRunning() != true) {
                        proxy = ByeDpiProxy()
                        val started = proxy?.startProxy(args) == true
                        isActive = started
                        if (started) updateNotification("Работает: 127.0.0.1:1080")
                        else {
                            updateNotification("Не удалось запустить ByeDPI")
                            proxy = null
                            isActive = false
                            stopForeground(true)
                            stopSelf()
                        }
                    } else {
                        isActive = true
                        updateNotification("Работает: 127.0.0.1:1080")
                    }
                } catch (error: Throwable) {
                    isActive = false
                    updateNotification("Ошибка запуска ByeDPI")
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        proxy?.stopProxy()
        proxy = null
        isActive = false
        stopForeground(true)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "ByeDPI connection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Geely YouTube · ByeDPI")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Geely YouTube · ByeDPI")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    companion object {
        const val ACTION_START = "com.romgold.geelyyoutube.action.START_BYEDPI"
        const val ACTION_STOP = "com.romgold.geelyyoutube.action.STOP_BYEDPI"
        const val EXTRA_ARGUMENTS = "byedpi_arguments"
        private const val CHANNEL_ID = "byedpi_proxy"
        private const val NOTIFICATION_ID = 1080

        @Volatile private var isActive = false
        fun isProxyRunning(): Boolean = isActive
    }
}
