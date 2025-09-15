package com.frivasm.manosquehablan

import android.app.Application
import android.util.Log
import com.frivasm.manosquehablan.notifications.NotificationManager as AppNotificationManager
import com.frivasm.manosquehablan.workers.VideoWorkManager

/**
 * Clase Application para inicialización global de la app
 * Maneja la configuración inicial y reanudación de trabajos pendientes
 */
class ManosQueHablanApp : Application() {
    
    companion object {
        private const val TAG = "ManosQueHablanApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Inicializando aplicación Manos Que Hablan")
        
        // Crear canales de notificación
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
            VideoWorkManager.resumePendingJobs(this)
            Log.d(TAG, "Trabajos pendientes verificados y reanudados si es necesario")
        } catch (e: Exception) {
            Log.e(TAG, "Error reanudando trabajos pendientes: ${e.message}")
        }
    }
}