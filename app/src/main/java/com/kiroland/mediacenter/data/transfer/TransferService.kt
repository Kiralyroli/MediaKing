package com.kiroland.mediacenter.data.transfer

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.kiroland.mediacenter.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the upload server alive while the user has it switched on. Android TV kills background apps
 * freely, so this runs in the foreground and holds a Wi-Fi lock (no power-save radio) and a partial
 * wake lock (CPU keeps writing to the drive) for as long as the server is up.
 */
@AndroidEntryPoint
class TransferService : Service() {

    @Inject lateinit var repository: TransferRepository

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        acquireLocks()
        repository.start()
        // Refresh the notification now that the address is known.
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        return START_STICKY
    }

    override fun onDestroy() {
        repository.stop()
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (wifiLock == null) {
            @Suppress("DEPRECATION") // WIFI_MODE_FULL_HIGH_PERF is the right mode for sustained transfers on API 29.
            wifiLock = getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MediaKing:upload")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediaKing:upload")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, AppLocale.text(R.string.upload_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TransferService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val state = repository.state.value
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(AppLocale.text(R.string.upload_notification_title))
            .setContentText(state.url ?: AppLocale.text(R.string.upload_notification_starting))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, AppLocale.text(R.string.upload_notification_stop), stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "transfer"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.kiroland.mediacenter.STOP_TRANSFER"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TransferService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TransferService::class.java))
        }
    }
}
