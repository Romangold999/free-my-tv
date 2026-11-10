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
    private var activeArgs: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                val stopped = proxy?.stopProxy() ?: true
                if (!stopped) {
                    updateNotification("Ожидаем завершения ByeDPI…")
                    return START_STICKY
                }
                proxy = null
                activeArgs = null
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val storedArgs = getSharedPreferences("dpi_profiles", MODE_PRIVATE)
                    .getString("last_byedpi_args", "").orEmpty()
                val args = intent?.getStringExtra(EXTRA_ARGUMENTS) ?: storedArgs
                if (args.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                getSharedPreferences("dpi_profiles", MODE_PRIVATE).edit()
                    .putString("last_byedpi_args", args).apply()
                startForeground(NOTIFICATION_ID, buildNotification("Запуск локального SOCKS5…"))
                try {
                    // Applying a different saved profile must replace the running
                    // native listener; silently keeping the old arguments is misleading.
                    val existing = proxy
                    if (existing != null && (!existing.isRunning() || activeArgs != args)) {
                        if (!existing.stopProxy()) {
                            updateNotification("Не удалось завершить предыдущий процесс ByeDPI")
                            stopForeground(true)
                            stopSelf()
                            return START_NOT_STICKY
                        }
                        proxy = null
                        activeArgs = null
                    }
                    if (proxy?.isRunning() != true) {
                        val candidate = ByeDpiProxy()
                        if (candidate.startProxy(args)) {
                            proxy = candidate
                            activeArgs = args
                            updateNotification("Работает: 127.0.0.1:1080")
                        } else {
                            candidate.stopProxy()
                            proxy = null
                            activeArgs = null
                            updateNotification("Не удалось запустить ByeDPI")
                            stopForeground(true)
                            stopSelf()
                        }
                    } else {
                        updateNotification("Работает: 127.0.0.1:1080")
                    }
                } catch (error: Throwable) {
                    proxy?.stopProxy()
                    proxy = null
                    activeArgs = null
                    updateNotification("Ошибка запуска ByeDPI: ${error.message ?: error.javaClass.simpleName}")
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
        activeArgs = null
        if (activeService === this) activeService = null
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
        statusListener?.invoke(message)
    }

    companion object {
        const val ACTION_START = "com.romgold.geelyyoutube.action.START_BYEDPI"
        const val ACTION_STOP = "com.romgold.geelyyoutube.action.STOP_BYEDPI"
        const val EXTRA_ARGUMENTS = "byedpi_arguments"
        private const val CHANNEL_ID = "byedpi_proxy"
        private const val NOTIFICATION_ID = 1080

        @Volatile private var activeService: ByeDpiService? = null
        @Volatile var statusListener: ((String) -> Unit)? = null
        fun isProxyRunning(): Boolean = activeService?.proxy?.isRunning() == true
    }
}
