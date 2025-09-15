package com.frivasm.manosquehablan.persistence

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * Manejador de persistencia para el estado de trabajos de procesamiento de video
 * Permite reanudar trabajos después de reinicios de la app o del sistema
 */
class VideoProcessingJobManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoProcessingJobManager"
        private const val PREFS_NAME = "video_processing_persistence"
        
        // Keys principales
        private const val KEY_ACTIVE_JOBS = "active_jobs"
        private const val KEY_JOB_PREFIX = "job_"
        private const val KEY_JOB_HISTORY = "job_history"
        
        // Estados de trabajo
        const val STATE_QUEUED = "queued"
        const val STATE_UPLOADING = "uploading"
        const val STATE_PROCESSING = "processing"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELLED = "cancelled"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Información de un trabajo de procesamiento
     */
    data class ProcessingJob(
        val id: String,
        val videoPath: String,
        val state: String,
        val createdAt: Long,
        val updatedAt: Long,
        val uploadId: String? = null,
        val sessionName: String? = null,
        val errorMessage: String? = null,
        val resultUrls: ResultUrls? = null,
        val progress: Progress? = null
    )
    
    data class ResultUrls(
        val videoUrl: String,
        val audioUrl: String,
        val textUrl: String
    )
    
    data class Progress(
        val state: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Crea un nuevo trabajo y lo persiste
     */
    fun createJob(videoPath: String): ProcessingJob {
        val jobId = "job_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        
        val job = ProcessingJob(
            id = jobId,
            videoPath = videoPath,
            state = STATE_QUEUED,
            createdAt = now,
            updatedAt = now
        )
        
        saveJob(job)
        addToActiveJobs(jobId)
        
        Log.i(TAG, "Trabajo creado: $jobId para video: $videoPath")
        return job
    }
    
    /**
     * Actualiza el estado de un trabajo
     */
    fun updateJobState(jobId: String, newState: String, message: String? = null, errorMessage: String? = null) {
        val job = getJob(jobId) ?: return
        
        val updatedJob = job.copy(
            state = newState,
            updatedAt = System.currentTimeMillis(),
            errorMessage = errorMessage,
            progress = Progress(newState, message ?: getDefaultMessageForState(newState))
        )
        
        saveJob(updatedJob)
        
        // Si el trabajo se completó o falló, remover de activos
        if (newState == STATE_COMPLETED || newState == STATE_FAILED || newState == STATE_CANCELLED) {
            removeFromActiveJobs(jobId)
            addToJobHistory(jobId)
        }
        
        Log.d(TAG, "Trabajo $jobId actualizado a estado: $newState")
    }
    
    /**
     * Guarda información de upload para un trabajo
     */
    fun saveJobUploadInfo(jobId: String, uploadId: String, videoUrl: String, audioUrl: String, textUrl: String) {
        val job = getJob(jobId) ?: return
        
        val resultUrls = ResultUrls(videoUrl, audioUrl, textUrl)
        val updatedJob = job.copy(
            uploadId = uploadId,
            resultUrls = resultUrls,
            updatedAt = System.currentTimeMillis()
        )
        
        saveJob(updatedJob)
        Log.d(TAG, "Información de upload guardada para trabajo: $jobId")
    }
    
    /**
     * Guarda el nombre de sesión para un trabajo
     */
    fun saveJobSessionName(jobId: String, sessionName: String) {
        val job = getJob(jobId) ?: return
        
        val updatedJob = job.copy(
            sessionName = sessionName,
            updatedAt = System.currentTimeMillis()
        )
        
        saveJob(updatedJob)
        Log.d(TAG, "Nombre de sesión guardado para trabajo $jobId: $sessionName")
    }
    
    /**
     * Obtiene un trabajo por ID
     */
    fun getJob(jobId: String): ProcessingJob? {
        val jobJson = prefs.getString("$KEY_JOB_PREFIX$jobId", null)
        return if (jobJson != null) {
            try {
                gson.fromJson(jobJson, ProcessingJob::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializando trabajo $jobId: ${e.message}")
                null
            }
        } else null
    }
    
    /**
     * Obtiene todos los trabajos activos
     */
    fun getActiveJobs(): List<ProcessingJob> {
        val activeJobIds = getActiveJobIds()
        return activeJobIds.mapNotNull { getJob(it) }
    }
    
    /**
     * Obtiene trabajos por estado
     */
    fun getJobsByState(state: String): List<ProcessingJob> {
        return getActiveJobs().filter { it.state == state }
    }
    
    /**
     * Verifica si hay trabajos pendientes que se puedan reanudar
     */
    fun hasResumableJobs(): Boolean {
        return getActiveJobs().any { job ->
            job.state in listOf(STATE_QUEUED, STATE_UPLOADING, STATE_PROCESSING, STATE_DOWNLOADING)
        }
    }
    
    /**
     * Obtiene trabajos que se pueden reanudar
     */
    fun getResumableJobs(): List<ProcessingJob> {
        return getActiveJobs().filter { job ->
            job.state in listOf(STATE_QUEUED, STATE_UPLOADING, STATE_PROCESSING, STATE_DOWNLOADING)
        }
    }
    
    /**
     * Marca un trabajo como cancelado
     */
    fun cancelJob(jobId: String) {
        updateJobState(jobId, STATE_CANCELLED, "Trabajo cancelado por el usuario")
    }
    
    /**
     * Limpia trabajos antiguos completados (más de 30 días)
     */
    fun cleanupOldJobs() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        val jobHistory = getJobHistory()
        
        val jobsToRemove = jobHistory.filter { jobId ->
            val job = getJob(jobId)
            job != null && job.updatedAt < thirtyDaysAgo && 
            (job.state == STATE_COMPLETED || job.state == STATE_FAILED)
        }
        
        jobsToRemove.forEach { jobId ->
            removeJob(jobId)
        }
        
        if (jobsToRemove.isNotEmpty()) {
            Log.i(TAG, "Limpiados ${jobsToRemove.size} trabajos antiguos")
        }
    }
    
    private fun saveJob(job: ProcessingJob) {
        val jobJson = gson.toJson(job)
        prefs.edit().putString("$KEY_JOB_PREFIX${job.id}", jobJson).apply()
    }
    
    private fun removeJob(jobId: String) {
        prefs.edit().remove("$KEY_JOB_PREFIX$jobId").apply()
        removeFromActiveJobs(jobId)
        removeFromJobHistory(jobId)
    }
    
    private fun getActiveJobIds(): List<String> {
        val activeJobsJson = prefs.getString(KEY_ACTIVE_JOBS, "[]")
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(activeJobsJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializando trabajos activos: ${e.message}")
            emptyList()
        }
    }
    
    private fun addToActiveJobs(jobId: String) {
        val activeJobs = getActiveJobIds().toMutableList()
        if (!activeJobs.contains(jobId)) {
            activeJobs.add(jobId)
            saveActiveJobIds(activeJobs)
        }
    }
    
    private fun removeFromActiveJobs(jobId: String) {
        val activeJobs = getActiveJobIds().toMutableList()
        if (activeJobs.remove(jobId)) {
            saveActiveJobIds(activeJobs)
        }
    }
    
    private fun saveActiveJobIds(jobIds: List<String>) {
        val activeJobsJson = gson.toJson(jobIds)
        prefs.edit().putString(KEY_ACTIVE_JOBS, activeJobsJson).apply()
    }
    
    private fun getJobHistory(): List<String> {
        val historyJson = prefs.getString(KEY_JOB_HISTORY, "[]")
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(historyJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializando historial: ${e.message}")
            emptyList()
        }
    }
    
    private fun addToJobHistory(jobId: String) {
        val history = getJobHistory().toMutableList()
        if (!history.contains(jobId)) {
            history.add(0, jobId) // Agregar al inicio
            // Mantener solo los últimos 100 trabajos en historial
            if (history.size > 100) {
                history.removeAt(history.size - 1)
            }
            saveJobHistory(history)
        }
    }
    
    private fun removeFromJobHistory(jobId: String) {
        val history = getJobHistory().toMutableList()
        if (history.remove(jobId)) {
            saveJobHistory(history)
        }
    }
    
    private fun saveJobHistory(history: List<String>) {
        val historyJson = gson.toJson(history)
        prefs.edit().putString(KEY_JOB_HISTORY, historyJson).apply()
    }
    
    private fun getDefaultMessageForState(state: String): String {
        return when (state) {
            STATE_QUEUED -> "Video en cola para procesamiento"
            STATE_UPLOADING -> "Subiendo video al servidor"
            STATE_PROCESSING -> "Procesando video"
            STATE_DOWNLOADING -> "Descargando archivos traducidos"
            STATE_COMPLETED -> "Traducción completada"
            STATE_FAILED -> "Error en el procesamiento"
            STATE_CANCELLED -> "Trabajo cancelado"
            else -> "Estado: $state"
        }
    }
}