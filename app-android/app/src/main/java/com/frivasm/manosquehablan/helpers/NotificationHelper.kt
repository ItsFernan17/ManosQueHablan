package com.frivasm.manosquehablan.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.frivasm.manosquehablan.InicioAppActivity
import com.frivasm.manosquehablan.R

/**
 * Helper para gestionar las notificaciones de la aplicación
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "video_translation_channel"
        private const val NOTIFICATION_ID_SUCCESS = 1001
        private const val NOTIFICATION_ID_ERROR = 1002
    }

    init {
        createNotificationChannel()
    }

    /**
     * Crea el canal de notificaciones (necesario para Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Muestra notificación cuando el video se procesa exitosamente
     */
    fun mostrarNotificacionVideoExitoso() {
        val intent = Intent(context, InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notif_video_procesado_titulo))
            .setContentText(context.getString(R.string.notif_video_procesado_mensaje))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SUCCESS, notification)
        } catch (e: SecurityException) {
            // El usuario no ha dado permisos de notificación
            // En Android 13+ esto requiere permiso explícito
        }
    }

    /**
     * Muestra notificación cuando hay error en el procesamiento
     */
    fun mostrarNotificacionVideoError() {
        val intent = Intent(context, InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notif_video_error_titulo))
            .setContentText(context.getString(R.string.notif_video_error_mensaje))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ERROR, notification)
        } catch (e: SecurityException) {
            // El usuario no ha dado permisos de notificación
        }
    }
}