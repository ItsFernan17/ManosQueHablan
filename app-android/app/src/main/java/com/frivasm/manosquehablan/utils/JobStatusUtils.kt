package com.frivasm.manosquehablan.utils

import android.content.Context
import android.util.Log
import androidx.work.WorkInfo
import com.frivasm.manosquehablan.persistence.VideoProcessingJobManager
import com.frivasm.manosquehablan.workers.VideoWorkManager

/**
 * Utilidades para mostrar información de trabajos de procesamiento
 * Útil para debugging y monitoreo del estado de la aplicación
 */
object JobStatusUtils {
    
    private const val TAG = "JobStatusUtils"
    
    /**
     * Obtiene un resumen del estado de todos los trabajos
     */
    fun getJobStatusSummary(context: Context): JobStatusSummary {
        val jobManager = VideoProcessingJobManager(context)
        val workStats = VideoWorkManager.getWorkStatistics(context)
        
        val activeJobs = jobManager.getActiveJobs()
        val hasResumableJobs = jobManager.hasResumableJobs()
        val hasActiveWork = VideoWorkManager.hasActiveWork(context)
        
        return JobStatusSummary(
            activeJobsCount = activeJobs.size,
            hasResumableJobs = hasResumableJobs,
            hasActiveWork = hasActiveWork,
            workManagerStats = workStats,
            activeJobs = activeJobs
        )
    }
    
    /**
     * Convierte estado de WorkManager a texto legible
     */
    fun workStateToText(state: WorkInfo.State?): String {
        return when (state) {
            WorkInfo.State.ENQUEUED -> "En cola"
            WorkInfo.State.RUNNING -> "Ejecutándose"
            WorkInfo.State.SUCCEEDED -> "Completado"
            WorkInfo.State.FAILED -> "Falló"
            WorkInfo.State.BLOCKED -> "Bloqueado"
            WorkInfo.State.CANCELLED -> "Cancelado"
            null -> "Desconocido"
        }
    }
    
    /**
     * Convierte estado de persistencia a texto legible
     */
    fun jobStateToText(state: String): String {
        return when (state) {
            VideoProcessingJobManager.STATE_QUEUED -> "En cola"
            VideoProcessingJobManager.STATE_UPLOADING -> "Subiendo"
            VideoProcessingJobManager.STATE_PROCESSING -> "Procesando"
            VideoProcessingJobManager.STATE_DOWNLOADING -> "Descargando"
            VideoProcessingJobManager.STATE_COMPLETED -> "Completado"
            VideoProcessingJobManager.STATE_FAILED -> "Falló"
            VideoProcessingJobManager.STATE_CANCELLED -> "Cancelado"
            else -> state
        }
    }
    
    /**
     * Registra estado de trabajos para debugging
     */
    fun logJobStatus(context: Context) {
        try {
            val summary = getJobStatusSummary(context)
            
            Log.i(TAG, "=== RESUMEN DE TRABAJOS ===")
            Log.i(TAG, "Trabajos activos en persistencia: ${summary.activeJobsCount}")
            Log.i(TAG, "¿Hay trabajos para reanudar?: ${summary.hasResumableJobs}")
            Log.i(TAG, "¿Hay trabajos activos en WorkManager?: ${summary.hasActiveWork}")
            Log.i(TAG, "WorkManager - Ejecutándose: ${summary.workManagerStats.running}")
            Log.i(TAG, "WorkManager - En cola: ${summary.workManagerStats.enqueued}")
            Log.i(TAG, "WorkManager - Exitosos: ${summary.workManagerStats.succeeded}")
            Log.i(TAG, "WorkManager - Fallidos: ${summary.workManagerStats.failed}")
            
            if (summary.activeJobs.isNotEmpty()) {
                Log.i(TAG, "=== TRABAJOS ACTIVOS ===")
                summary.activeJobs.forEach { job ->
                    Log.i(TAG, "Job ${job.id}: ${jobStateToText(job.state)} - ${job.videoPath}")
                    job.progress?.let { progress ->
                        Log.i(TAG, "  -> ${progress.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando estado de trabajos: ${e.message}")
        }
    }
    
    data class JobStatusSummary(
        val activeJobsCount: Int,
        val hasResumableJobs: Boolean,
        val hasActiveWork: Boolean,
        val workManagerStats: VideoWorkManager.WorkStatistics,
        val activeJobs: List<VideoProcessingJobManager.ProcessingJob>
    )
}