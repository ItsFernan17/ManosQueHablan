package com.frivasm.manosquehablan.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.frivasm.manosquehablan.workers.VideoWorkManager

/**
 * BroadcastReceiver para manejar acciones de notificaciones
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "CANCEL_UPLOAD" -> {
                val sessionId = intent.getStringExtra("sessionId")
                if (sessionId != null) {
                    Log.i(TAG, "Cancelando subida de video desde notificación - Sesión: $sessionId")

                    // Cancelar el trabajo de WorkManager
                    val cancelled = VideoWorkManager.cancelVideoProcessing(context, sessionId)

                    if (cancelled) {
                        Log.i(TAG, "Trabajo cancelado exitosamente desde notificación")

                        // Mostrar notificación de éxito (cancelación exitosa)
                        NotificationManager.showSuccessNotification(
                            context = context,
                            title = "Subida cancelada",
                            message = "La subida del video fue cancelada correctamente",
                            isWarning = false
                        )
                    } else {
                        Log.w(TAG, "No se pudo cancelar el trabajo desde notificación")
                        // Mostrar notificación de error amigable
                        NotificationManager.showErrorNotification(
                            context = context,
                            title = "No se pudo cancelar",
                            message = "La subida continúa en proceso. Inténtalo de nuevo."
                        )
                    }
                } else {
                    Log.w(TAG, "SessionId nulo en acción de cancelar")
                }
            }
            else -> {
                Log.w(TAG, "Acción desconocida recibida: ${intent.action}")
            }
        }
    }
}