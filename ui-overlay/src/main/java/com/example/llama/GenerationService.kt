package com.example.llama

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

class GenerationService : Service() {

    companion object {
        private const val CHANNEL_ID = "generation"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GenerationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GenerationService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.generation_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.generation_channel_desc)
        nm.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.generation_notification_title))
            .setContentText(getString(R.string.generation_notification_text))
            .setSmallIcon(R.drawable.ic_generation)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
