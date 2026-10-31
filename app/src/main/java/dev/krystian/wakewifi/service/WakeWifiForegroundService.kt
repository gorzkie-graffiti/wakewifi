package dev.krystian.wakewifi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.krystian.wakewifi.MainActivity
import dev.krystian.wakewifi.audio.AlarmEngine
import dev.krystian.wakewifi.network.WifiMonitorManager

class WakeWifiForegroundService : Service() {

    private lateinit var wifiMonitorManager: WifiMonitorManager
    private lateinit var alarmEngine: AlarmEngine

    companion object {
        const val CHANNEL_ID = "WakeWifiServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "ACTION_STOP_MONITORING"
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"

        fun startService(context: Context) {
            Log.d("WakeWifi", "WakeWifiForegroundService: startService requested")
            val intent = Intent(context, WakeWifiForegroundService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            Log.d("WakeWifi", "WakeWifiForegroundService: stopService requested")
            val intent = Intent(context, WakeWifiForegroundService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun stopAlarm(context: Context) {
            Log.d("WakeWifi", "WakeWifiForegroundService: stopAlarm requested")
            val intent = Intent(context, WakeWifiForegroundService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WakeWifi", "WakeWifiForegroundService: onCreate")
        alarmEngine = AlarmEngine(this)
        wifiMonitorManager = WifiMonitorManager(this) {
            Log.d("WakeWifi", "WakeWifiForegroundService: Wi-Fi connected callback triggered! Starting alarm and updating notification.")
            AlarmEngine.startAlarm(this)
            updateNotification("Wi-Fi Connected! Alarm ringing!")
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WakeWifi", "WakeWifiForegroundService: onStartCommand action=${intent?.action}, flags=$flags, startId=$startId")
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                Log.d("WakeWifi", "WakeWifiForegroundService: Stopping monitoring and service")
                wifiMonitorManager.stopMonitoring()
                AlarmEngine.stopAlarm(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP_ALARM -> {
                Log.d("WakeWifi", "WakeWifiForegroundService: Stopping active alarm")
                AlarmEngine.stopAlarm(this)
                updateNotification("Monitoring Wi-Fi connection...")
            }
            else -> {
                Log.d("WakeWifi", "WakeWifiForegroundService: Starting foreground and Wi-Fi monitoring")
                createNotificationChannel()
                val notification = createNotification("Monitoring Wi-Fi connection...")
                try {
                    val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    } else {
                        0
                    }
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        foregroundType
                    )
                    wifiMonitorManager.startMonitoring()
                } catch (e: Exception) {
                    Log.e("WakeWifi", "WakeWifiForegroundService: Failed to start foreground service", e)
                    e.printStackTrace()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("WakeWifi", "WakeWifiForegroundService: onDestroy")
        wifiMonitorManager.stopMonitoring()
        AlarmEngine.stopAlarm(this)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "WakeWifi Monitoring Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors Wi-Fi connection and triggers alarm"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopAlarmIntent = Intent(this, WakeWifiForegroundService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopAlarmPendingIntent = PendingIntent.getService(
            this,
            1,
            stopAlarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WakeWifi Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Alarm", stopAlarmPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
