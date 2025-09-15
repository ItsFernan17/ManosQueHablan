package com.frivasm.manosquehablan.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.frivasm.manosquehablan.notifications.NotificationManager as AppNotificationManager
import com.frivasm.manosquehablan.persistence.VideoProcessingJobManager
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Manejador centralizado para trabajos de WorkManager
 * Encola, observa y maneja trabajos de procesamiento de video de forma robusta
 */
object VideoWorkManager {
    
    private const val TAG = "VideoWorkManager"
    
    // Nombres únicos para trabajos
    private const val WORK_NAME_PREFIX = "video_processing_"
    
    /**
     * Encola un nuevo trabajo de procesamiento de video
     * Utiliza unique work para evitar duplicados y permitir reintentos con backoff
     */
    fun enqueueVideoProcessing(
        context: Context,
        videoPath: String,
        allowReplace: Boolean = false
    ): String {
        
        // Crear identificador único para este video
        val sessionId = UUID.randomUUID().toString()
        val uniqueWorkName = "$WORK_NAME_PREFIX$sessionId"
        
        Log.i(TAG, "Encolando procesamiento de video: $videoPath (trabajo: $uniqueWorkName)")
        
        // Crear canales de notificación
        AppNotificationManager.createNotificationChannels(context)
        
        // Configurar restricciones del trabajo
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Permitir con batería baja
            .setRequiresStorageNotLow(true)  // Necesitamos espacio para descargas
            .build()
        
        // Crear petición de trabajo
        val workRequest = OneTimeWorkRequestBuilder<ProcesoVideoWorker>()
            .setInputData(workDataOf(
                ProcesoVideoWorker.KEY_VIDEO_PATH to videoPath,
                ProcesoVideoWorker.KEY_SESSION_ID to sessionId
            ))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS // Empezar con 30 segundos, duplicar en cada intento
            )
            .addTag("video_processing")
            .addTag(sessionId)
            .build()
        
        // Política de trabajo único
        val existingWorkPolicy = if (allowReplace) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP // Mantener el trabajo existente si ya hay uno
        }
        
        // Encolar trabajo único
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            existingWorkPolicy,
            workRequest
        )
        
        // Mostrar notificación de encolado
        AppNotificationManager.showQueuedNotification(
            context,
            "Video en cola",
            "Tu video está siendo preparado para procesamiento"
        )
        
        Log.i(TAG, "Trabajo encolado exitosamente: $uniqueWorkName")
        return sessionId
    }
    
    /**
     * Observa el progreso de un trabajo específico
     */
    fun observeWorkProgress(
        context: Context,
        sessionId: String,
        observer: (WorkInfo?) -> Unit
    ) {
        val uniqueWorkName = "$WORK_NAME_PREFIX$sessionId"
        
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(uniqueWorkName)
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                observer(workInfo)
            }
    }
    
    /**
     * Cancela un trabajo de procesamiento
     */
    fun cancelVideoProcessing(context: Context, sessionId: String): Boolean {
        return try {
            val uniqueWorkName = "$WORK_NAME_PREFIX$sessionId"
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
            
            // Actualizar estado en persistencia
            val jobManager = VideoProcessingJobManager(context)
            jobManager.getActiveJobs().find { it.id.contains(sessionId) }?.let { job ->
                jobManager.cancelJob(job.id)
            }
            
            Log.i(TAG, "Trabajo cancelado: $uniqueWorkName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando trabajo: ${e.message}")
            false
        }
    }
    
    /**
     * Obtiene información del estado actual de un trabajo
     */
    fun getWorkStatus(context: Context, sessionId: String): WorkInfo? {
        val uniqueWorkName = "$WORK_NAME_PREFIX$sessionId"
        
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(uniqueWorkName)
                .get()
            
            workInfos.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo estado del trabajo: ${e.message}")
            null
        }
    }
    
    /**
     * Verifica si hay trabajos de procesamiento activos
     */
    fun hasActiveWork(context: Context): Boolean {
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosByTag("video_processing")
                .get()
            
            workInfos.any { workInfo ->
                workInfo.state == WorkInfo.State.RUNNING || 
                workInfo.state == WorkInfo.State.ENQUEUED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando trabajos activos: ${e.message}")
            false
        }
    }
    
    /**
     * Reanudar trabajos pendientes después de reinicio de la app
     */
    fun resumePendingJobs(context: Context) {
        try {
            val jobManager = VideoProcessingJobManager(context)
            val pendingJobs = jobManager.getResumableJobs()
            
            if (pendingJobs.isNotEmpty()) {
                Log.i(TAG, "Encontrados ${pendingJobs.size} trabajos para reanudar")
                
                pendingJobs.forEach { job ->
                    // Verificar si el trabajo aún existe en WorkManager
                    val sessionId = job.id.substringAfter("job_")
                    val workInfo = getWorkStatus(context, sessionId)
                    
                    if (workInfo == null || workInfo.state.isFinished) {
                        // Trabajo no existe en WorkManager, encolar de nuevo
                        Log.i(TAG, "Reanudando trabajo: ${job.id} para video: ${job.videoPath}")
                        enqueueVideoProcessing(context, job.videoPath, allowReplace = true)
                    }
                }
            }
            
            // Limpiar trabajos antiguos
            jobManager.cleanupOldJobs()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reanudando trabajos: ${e.message}")
        }
    }
    
    /**
     * Obtiene estadísticas de trabajos
     */
    fun getWorkStatistics(context: Context): WorkStatistics {
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosByTag("video_processing")
                .get()
            
            val running = workInfos.count { it.state == WorkInfo.State.RUNNING }
            val enqueued = workInfos.count { it.state == WorkInfo.State.ENQUEUED }
            val succeeded = workInfos.count { it.state == WorkInfo.State.SUCCEEDED }
            val failed = workInfos.count { it.state == WorkInfo.State.FAILED }
            
            WorkStatistics(running, enqueued, succeeded, failed)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo estadísticas: ${e.message}")
            WorkStatistics(0, 0, 0, 0)
        }
    }
    
    data class WorkStatistics(
        val running: Int,
        val enqueued: Int,
        val succeeded: Int,
        val failed: Int
    )
}