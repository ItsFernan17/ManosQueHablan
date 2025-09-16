package com.frivasm.manosquehablan.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frivasm.manosquehablan.InicioAppActivity
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
     * Crea todos los canales de notificación necesarios y limpia duplicados
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // LIMPIAR CANALES DUPLICADOS ANTES DE CREAR NUEVOS
            cleanDuplicateChannels(notificationManager)

            // Canal para procesamiento en curso
            val processingChannel = NotificationChannel(
                CHANNEL_ID_PROCESSING,
                "Procesamiento de video",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones del procesamiento de traducción en curso"
                setShowBadge(false)
                enableVibration(false) // SIN VIBRACIÓN
                setSound(null, null) // SIN SONIDO para notificaciones de subida
                setImportance(NotificationManager.IMPORTANCE_LOW) // Baja importancia
            }

            // Canal para completados - FORZAR IMPORTANCE_HIGH para heads-up
            val completedChannel = NotificationChannel(
                CHANNEL_ID_COMPLETED,
                "Traducción completada",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando la traducción se completa exitosamente"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
                // FORZAR configuración para heads-up
                setImportance(NotificationManager.IMPORTANCE_HIGH)
            }

            // Canal para errores - FORZAR IMPORTANCE_HIGH para heads-up
            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERROR,
                "Errores de traducción",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de errores durante el procesamiento"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
                // FORZAR configuración para heads-up
                setImportance(NotificationManager.IMPORTANCE_HIGH)
            }

            notificationManager.createNotificationChannels(listOf(
                processingChannel,
                completedChannel,
                errorChannel
            ))

            Log.d("NotificationManager", "Canales de notificación creados y duplicados limpiados")
        }
    }

    /**
     * Limpia canales de notificación duplicados
     */
    private fun cleanDuplicateChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val existingChannels = notificationManager.notificationChannels
                val channelsToDelete = mutableListOf<String>()

                // Buscar canales duplicados o antiguos
                existingChannels.forEach { channel ->
                    when {
                        // Eliminar canal duplicado de NotificationHelper
                        channel.id == "video_translation_channel" -> {
                            channelsToDelete.add(channel.id)
                            Log.d("NotificationManager", "Canal duplicado encontrado: ${channel.id}")
                        }
                        // Verificar si nuestros canales necesitan actualización
                        channel.id in listOf(CHANNEL_ID_PROCESSING, CHANNEL_ID_COMPLETED, CHANNEL_ID_ERROR) -> {
                            // Forzar importancia alta para canales de éxito y error
                            if (channel.id != CHANNEL_ID_PROCESSING && channel.importance != NotificationManager.IMPORTANCE_HIGH) {
                                channel.importance = NotificationManager.IMPORTANCE_HIGH
                                notificationManager.createNotificationChannel(channel)
                                Log.d("NotificationManager", "Canal actualizado a IMPORTANCE_HIGH: ${channel.id}")
                            }
                        }
                    }
                }

                // Eliminar canales duplicados
                channelsToDelete.forEach { channelId ->
                    notificationManager.deleteNotificationChannel(channelId)
                    Log.d("NotificationManager", "Canal duplicado eliminado: $channelId")
                }

            } catch (e: Exception) {
                Log.e("NotificationManager", "Error limpiando canales duplicados: ${e.message}")
            }
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
            .setPriority(NotificationCompat.PRIORITY_LOW) // Baja prioridad para procesamiento
            .setSound(null) // SIN SONIDO para notificaciones de subida
            .setVibrate(null) // SIN VIBRACIÓN para notificaciones de subida
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS) // Categoría apropiada
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

        // Crear PendingIntent para abrir la app
        val intent = Intent(context, InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extra para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isWarning) NOTIFICATION_ID_ERROR else NOTIFICATION_ID_COMPLETED, // requestCode único
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Crear FullScreenIntent para forzar heads-up en algunos dispositivos
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            if (isWarning) NOTIFICATION_ID_ERROR + 100 else NOTIFICATION_ID_COMPLETED + 100,
            Intent(context, InicioAppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_notification", true)
                putExtra("fullscreen_notification", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX) // MÁXIMA PRIORIDAD para heads-up
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Vibración
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setOnlyAlertOnce(false) // Permitir múltiples alertas
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Categoría apropiada
            .setDefaults(NotificationCompat.DEFAULT_SOUND) // FORZAR SONIDO DEL SISTEMA
            .setFullScreenIntent(fullScreenIntent, true) // FORZAR HEADS-UP
            .setContentIntent(pendingIntent) // Agregar PendingIntent para abrir app
            .build()

        val notificationId = if (isWarning) NOTIFICATION_ID_ERROR else NOTIFICATION_ID_COMPLETED

        // Asegurar que se muestre la notificación
        try {
            notificationManager.notify(notificationId, notification)
            Log.d("NotificationManager", "Notificación mostrada exitosamente - ID: $notificationId, isWarning: $isWarning")

            // Forzar actualización del canal para asegurar configuración
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel(channelId)
                if (channel != null) {
                    channel.importance = NotificationManager.IMPORTANCE_HIGH
                    notificationManager.createNotificationChannel(channel)
                    Log.d("NotificationManager", "Canal actualizado para forzar heads-up: $channelId")
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error mostrando notificación: ${e.message}")
        }
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

        // Crear PendingIntent para abrir la app
        val intent = Intent(context, InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extra para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ERROR, // requestCode único
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Crear FullScreenIntent para forzar heads-up en algunos dispositivos
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ERROR + 100,
            Intent(context, InicioAppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_notification", true)
                putExtra("fullscreen_notification", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ERROR)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX) // MÁXIMA PRIORIDAD para heads-up
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Vibración más fuerte para errores
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setOnlyAlertOnce(false) // Permitir múltiples alertas
            .setCategory(NotificationCompat.CATEGORY_ERROR) // Categoría apropiada
            .setDefaults(NotificationCompat.DEFAULT_SOUND) // FORZAR SONIDO DEL SISTEMA
            .setFullScreenIntent(fullScreenIntent, true) // FORZAR HEADS-UP
            .setContentIntent(pendingIntent) // Agregar PendingIntent para abrir app
            .build()

        // Asegurar que se muestre la notificación
        try {
            notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
            Log.d("NotificationManager", "Notificación de error mostrada exitosamente")

            // Forzar actualización del canal para asegurar configuración
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel(CHANNEL_ID_ERROR)
                if (channel != null) {
                    channel.importance = NotificationManager.IMPORTANCE_HIGH
                    notificationManager.createNotificationChannel(channel)
                    Log.d("NotificationManager", "Canal de error actualizado para forzar heads-up")
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error mostrando notificación de error: ${e.message}")
        }
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

        // Crear PendingIntent para abrir la app
        val intent = Intent(context, InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extra para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_PROCESSING + 10, // requestCode único
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PROCESSING)
            .setSmallIcon(R.drawable.ic_queue)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null) // SIN SONIDO
            .setVibrate(null) // SIN VIBRACIÓN
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent) // Agregar PendingIntent para abrir app
            .build()

        // Asegurar que se muestre la notificación
        try {
            notificationManager.notify(NOTIFICATION_ID_PROCESSING + 10, notification)
            Log.d("NotificationManager", "Notificación en cola mostrada exitosamente")
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error mostrando notificación en cola: ${e.message}")
        }
    }
}