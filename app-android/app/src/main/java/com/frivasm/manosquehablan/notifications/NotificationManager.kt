package com.frivasm.manosquehablan.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.frivasm.manosquehablan.R

/**
 * Manejador centralizado de notificaciones para el procesamiento de video
 */
object NotificationManager {
    
    const val CHANNEL_ID_PROCESSING = "proceso_traduccion"
    const val CHANNEL_ID_COMPLETED = "traduccion_completada"
    const val CHANNEL_ID_ERROR = "traduccion_error"
    
    const val NOTIFICATION_ID_PROCESSING = 1001
    const val NOTIFICATION_ID_COMPLETED = 1002
    const val NOTIFICATION_ID_ERROR = 1003

    /**
     * Crea todos los canales de notificación necesarios
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para procesamiento en curso
            val processingChannel = NotificationChannel(
                CHANNEL_ID_PROCESSING,
                "Procesamiento de video",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones del procesamiento de traducción en curso"
                setShowBadge(false)
                enableVibration(false)
            }
            
            // Canal para completados
            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                "Traducción completada",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones cuando la traducción se completa exitosamente"
                setShowBadge(true)
            }
            
            // Canal para errores
            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERROR,
                "Errores de traducción",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de errores durante el procesamiento"
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannels(listOf(
                processingChannel,
                completedChannel,
                errorChannel
            ))
        }
    }

    /**
     * Crea notificación para procesamiento en curso (foreground service)
     */
    fun createProcessingNotification(
        context: Context,
        title: String,
        message: String,
        showProgress: Boolean = true
    ): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_PROCESSING)
            .setSmallIcon(R.drawable.ic_stat_mqh)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (showProgress) {
                    setProgress(0, 0, true) // Indeterminado
                }
            }
            .build()
    }

    /**
     * Muestra notificación de éxito
     */
    fun showSuccessNotification(
        context: Context,
        title: String = "¡Traducción lista!",
        message: String = "Tu video fue procesado correctamente",
        isWarning: Boolean = false
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channelId = if (isWarning) CHANNEL_ID_ERROR else CHANNEL_ID_COMPLETED
        val icon = if (isWarning) R.drawable.ic_warning else R.drawable.ic_stat_mqh
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            
        val notificationId = if (isWarning) NOTIFICATION_ID_ERROR else NOTIFICATION_ID_COMPLETED
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Muestra notificación de error
     */
    fun showErrorNotification(
        context: Context,
        title: String = "Error en traducción",
        message: String = "Ocurrió un problema procesando tu video"
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }

    /**
     * Muestra notificación de video en cola
     */
    fun showQueuedNotification(
        context: Context,
        title: String = "Video en cola",
        message: String = "Tu video está en cola para ser procesado"
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROCESSING)
            .setSmallIcon(R.drawable.ic_queue)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID_PROCESSING + 10, notification)
    }
}