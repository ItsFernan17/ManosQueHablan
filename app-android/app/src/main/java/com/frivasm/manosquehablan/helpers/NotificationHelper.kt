package com.frivasm.manosquehablan.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.frivasm.manosquehablan.InicioAppActivity
import com.frivasm.manosquehablan.R

/**
 * Helper para gestionar las notificaciones de la aplicación
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "video_translation_channel"
        const val NOTIFICATION_ID_PROCESSING = 1000
        const val NOTIFICATION_ID_SUCCESS = 1001
        const val NOTIFICATION_ID_ERROR = 1002
        const val REQUEST_CODE_NOTIFICATION_PERMISSION = 2001
    }

    init {
        createNotificationChannel()
    }

    /**
     * Crea el canal de notificaciones (necesario para Android 8.0+)
     * Configurado para comportamiento similar a Gmail/Twitter
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH // HIGH para heads-up notifications
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250) // Patrón de vibración personalizado
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC // Mostrar en pantalla de bloqueo
                setShowBadge(true) // Mostrar badge en ícono de la app
                setBypassDnd(true) // Saltar modo No Molestar para emergentes

                // Configuración adicional para heads-up
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setImportance(NotificationManager.IMPORTANCE_HIGH)
                    setSound(null, null) // Sin sonido personalizado para usar el por defecto
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d("NotificationHelper", "Canal de notificaciones creado con IMPORTANCE_HIGH para heads-up")
        }
    }

    /**
     * Muestra UNA SOLA notificación unificada con el ícono de la app
     * Mensaje: "Procesando tu video" - persiste como Gmail/Twitter
     * APARECE TANTO EN PRIMER PLANO COMO EN SEGUNDO PLANO
     */
    fun mostrarNotificacionProcesandoVideo() {
        // Cancelar TODAS las notificaciones anteriores para evitar duplicados
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROCESSING)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SUCCESS)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ERROR)

        val intent = Intent(context, com.frivasm.manosquehablan.InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extras para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2, // Diferente requestCode para evitar conflictos
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("NotificationHelper", "PendingIntent creado para InicioAppActivity: ${intent.component}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mqh) // ÍCONO ESPECÍFICO PARA NOTIFICACIONES
            .setContentTitle("Manos que Hablan") // Título de la app
            .setContentText("Subiendo tu video...") // MENSAJE SOLICITADO
            .setPriority(NotificationCompat.PRIORITY_MAX) // MÁXIMA PRIORIDAD - EMERGENTE
            .setContentIntent(pendingIntent)
            .setAutoCancel(false) // NO se auto-cancela - persiste como Gmail/Twitter
            .setOngoing(true) // HACERLA ONGOING para que aparezca siempre
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setSound(Uri.EMPTY, 0) // SIN SONIDO para notificación de subida
            .setWhen(System.currentTimeMillis()) // Timestamp actual
            .setShowWhen(true) // Mostrar timestamp
            .setCategory(NotificationCompat.CATEGORY_PROGRESS) // Categoría de progreso
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setOnlyAlertOnce(true) // Solo alertar una vez, no cada actualización
            .build()

        try {
            // Verificar permiso de notificaciones en Android 13+
            if (isNotificationPermissionGranted()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROCESSING, notification)
                Log.d("NotificationHelper", "Notificación de procesamiento mostrada con logo - Visible en primer y segundo plano")
            } else {
                // Si no hay permiso, intentar solicitarlo (solo si el contexto es una Activity)
                if (context is android.app.Activity) {
                    requestNotificationPermission(context)
                }
            }
        } catch (e: SecurityException) {
            // El usuario no ha dado permisos de notificación
            // En Android 13+ esto requiere permiso explícito
            Log.w("NotificationHelper", "Permiso de notificación denegado - no se puede mostrar notificación")
        }
    }

    /**
     * Muestra notificación de éxito con el ícono de la app
     * APARECE TANTO EN PRIMER PLANO COMO EN SEGUNDO PLANO
     */
    fun mostrarNotificacionVideoExitoso() {
        // Cancelar notificaciones anteriores (procesamiento, éxito y error)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROCESSING)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SUCCESS)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ERROR)

        val intent = Intent(context, com.frivasm.manosquehablan.InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extras para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("NotificationHelper", "PendingIntent creado para notificación exitosa: ${intent.component}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mqh) // ÍCONO ESPECÍFICO PARA NOTIFICACIONES
            .setContentTitle("Manos que Hablan")
            .setContentText("¡Tu video ha sido procesado exitosamente!")
            .setPriority(NotificationCompat.PRIORITY_MAX) // MÁXIMA PRIORIDAD - EMERGENTE
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Se auto-cancela cuando se toca
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setOnlyAlertOnce(true) // Solo alertar una vez
            .build()

        try {
            if (isNotificationPermissionGranted()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SUCCESS, notification)
                Log.d("NotificationHelper", "Notificación de éxito mostrada con logo - Visible en primer y segundo plano")
            } else {
                if (context is android.app.Activity) {
                    requestNotificationPermission(context)
                }
            }
        } catch (e: SecurityException) {
            // El usuario no ha dado permisos de notificación
            Log.w("NotificationHelper", "Permiso de notificación denegado - no se puede mostrar notificación de éxito")
        }
    }

    /**
     * Muestra notificación de error con el ícono de la app
     * APARECE TANTO EN PRIMER PLANO COMO EN SEGUNDO PLANO
     */
    fun mostrarNotificacionVideoError() {
        // Cancelar notificaciones anteriores (procesamiento, éxito y error)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROCESSING)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SUCCESS)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ERROR)

        val intent = Intent(context, com.frivasm.manosquehablan.InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extras para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            4, // Diferente requestCode para evitar conflictos
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("NotificationHelper", "PendingIntent creado para notificación error: ${intent.component}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mqh) // ÍCONO ESPECÍFICO PARA NOTIFICACIONES
            .setContentTitle("Manos que Hablan")
            .setContentText("Error al procesar el video")
            .setPriority(NotificationCompat.PRIORITY_MAX) // MÁXIMA PRIORIDAD - EMERGENTE
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Se auto-cancela cuando se toca
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setOnlyAlertOnce(true) // Solo alertar una vez
            .build()

        try {
            if (isNotificationPermissionGranted()) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ERROR, notification)
                Log.d("NotificationHelper", "Notificación de error mostrada con logo - Visible en primer y segundo plano")
            } else {
                if (context is android.app.Activity) {
                    requestNotificationPermission(context)
                }
            }
        } catch (e: SecurityException) {
            // El usuario no ha dado permisos de notificación
            Log.w("NotificationHelper", "Permiso de notificación denegado - no se puede mostrar notificación de error")
        }
    }

    /**
     * Verifica si el permiso de notificaciones está concedido (Android 13+)
     */
    fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true // En versiones anteriores, no se requiere permiso explícito
    }

    /**
     * Solicita permiso de notificaciones si es necesario (Android 13+)
     */
    fun requestNotificationPermission(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted()) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    /**
     * Crea y retorna una notificación de procesamiento para foreground service
     * NO la muestra automáticamente - solo la retorna para uso en ForegroundInfo
     */
    fun crearNotificacionProcesandoVideo(): android.app.Notification {
        // Cancelar TODAS las notificaciones anteriores para evitar duplicados
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROCESSING)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SUCCESS)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ERROR)

        val intent = Intent(context, com.frivasm.manosquehablan.InicioAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Agregar extras para identificar que viene de notificación
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2, // Diferente requestCode para evitar conflictos
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("NotificationHelper", "PendingIntent creado para InicioAppActivity: ${intent.component}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mqh) // ÍCONO ESPECÍFICO PARA NOTIFICACIONES
            .setContentTitle("Manos que Hablan") // Título de la app
            .setContentText("Subiendo tu video...") // MENSAJE SOLICITADO
            .setPriority(NotificationCompat.PRIORITY_MAX) // MÁXIMA PRIORIDAD - EMERGENTE
            .setContentIntent(pendingIntent)
            .setAutoCancel(false) // NO se auto-cancela - persiste como Gmail/Twitter
            .setOngoing(true) // HACERLA ONGOING para que aparezca siempre
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setSound(Uri.EMPTY, 0) // SIN SONIDO para notificación de subida
            .setWhen(System.currentTimeMillis()) // Timestamp actual
            .setShowWhen(true) // Mostrar timestamp
            .setCategory(NotificationCompat.CATEGORY_PROGRESS) // Categoría de progreso
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla de bloqueo
            .setOnlyAlertOnce(true) // Solo alertar una vez, no cada actualización
            .build()

        return notification
    }
}