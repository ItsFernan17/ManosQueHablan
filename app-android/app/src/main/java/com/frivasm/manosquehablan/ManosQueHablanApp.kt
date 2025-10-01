package com.frivasm.manosquehablan

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.frivasm.manosquehablan.notifications.NotificationManager as AppNotificationManager
import com.frivasm.manosquehablan.workers.VideoWorkManager

/**
 * Application class for global app initialization
 * Handles initial setup and pending work resumption
 *
 * Clase Application para inicialización global de la app
 * Maneja la configuración inicial y reanudación de trabajos pendientes
 */
class ManosQueHablanApp : Application(), androidx.work.Configuration.Provider {

    companion object {
        private const val TAG = "ManosQueHablanApp"
    }

    override val workManagerConfiguration =
        androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Inicializando aplicación Manos Que Hablan")

        // Crear canales de notificación ANTES de cualquier uso de FGS/WorkManager
        setupNotificationChannels()

        // Reanudar trabajos pendientes de WorkManager
        resumePendingWork()

        Log.i(TAG, "Aplicación inicializada correctamente")
    }
    
    private fun setupNotificationChannels() {
        try {
            AppNotificationManager.createNotificationChannels(this)
            Log.d(TAG, "Canales de notificación creados")
        } catch (e: Exception) {
            Log.e(TAG, "Error creando canales de notificación: ${e.message}")
        }
    }
    
    private fun resumePendingWork() {
        try {
            // NO reanudar automáticamente los trabajos pendientes
            // En su lugar, verificar si hay trabajos pendientes y dejar que la UI principal los maneje
            val jobManager = com.frivasm.manosquehablan.persistence.VideoProcessingJobManager(this)
            val pendingJobs = jobManager.getResumableJobs()

            if (pendingJobs.isNotEmpty()) {
                Log.i(TAG, "=== APP RESTART === Se encontraron ${pendingJobs.size} trabajos pendientes. La UI principal preguntará al usuario si desea reanudarlos.")
            } else {
                Log.d(TAG, "=== APP RESTART === No hay trabajos pendientes para reanudar")
            }

            // Solo limpiar trabajos antiguos, no reanudar automáticamente
            jobManager.cleanupOldJobs()

        } catch (e: Exception) {
            Log.e(TAG, "Error verificando trabajos pendientes: ${e.message}")
        }
    }

}