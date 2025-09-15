package com.frivasm.manosquehablan.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.api.ApiCliente
import com.frivasm.manosquehablan.api.RespuestaProcesamiento
import com.frivasm.manosquehablan.config.ServerConfig
import com.frivasm.manosquehablan.helpers.ConectividadHelper
import com.frivasm.manosquehablan.helpers.VideoStorageManager
import com.frivasm.manosquehablan.helpers.VideoTranslationStatusHelper
import com.frivasm.manosquehablan.persistence.VideoProcessingJobManager
import com.frivasm.manosquehablan.notifications.NotificationManager as AppNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Worker robusto para procesamiento de video que encapsula todo el flujo:
 * 1. Subir video al servidor
 * 2. Monitorear estado del procesamiento 
 * 3. Descargar resultados cuando estén listos
 * 
 * Funciona en primer plano con notificaciones y estado persistente.
 */
class ProcesoVideoWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ProcesoVideoWorker"
        const val CHANNEL_ID = "proceso_traduccion"
        const val NOTIFICATION_ID = 1001
        
        // Keys para input data
        const val KEY_VIDEO_PATH = "videoPath"
        const val KEY_SESSION_ID = "sessionId"
        
        // Keys para progreso
        const val PROGRESS_STATE = "state"
        const val PROGRESS_MESSAGE = "message"
        
        // Estados del procesamiento
        const val STATE_CONECTANDO = "conectando"
        const val STATE_SUBIENDO = "subiendo"
        const val STATE_PROCESANDO = "procesando"
        const val STATE_DESCARGANDO = "descargando"
        const val STATE_COMPLETADO = "completado"
        const val STATE_ERROR = "error"
        
        // SharedPreferences para persistencia
        private const val PREFS_NAME = "video_processing_jobs"
        private const val KEY_CURRENT_JOB = "current_job_"
        private const val KEY_UPLOAD_ID = "upload_id_"
        private const val KEY_JOB_STATE = "job_state_"
    }

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val jobManager = VideoProcessingJobManager(appContext)

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString(KEY_VIDEO_PATH) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: UUID.randomUUID().toString()
        
        Log.i(TAG, "Iniciando procesamiento de video: $videoPath (sesión: $sessionId)")
        
        // Crear trabajo en sistema de persistencia
        val job = jobManager.createJob(videoPath)
        
        try {
            // Crear canal de notificación si no existe
            AppNotificationManager.createNotificationChannels(appContext)
            
            // Configurar foreground service
            setForeground(createForegroundInfo("Conectando con el servidor...", STATE_CONECTANDO))
            
            // 1. Verificar conectividad
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_UPLOADING, "Verificando conexión...")
            updateProgress(STATE_CONECTANDO, "Verificando conexión...")
            val conectividadOk = verificarConectividad()
            if (!conectividadOk) {
                return handleError(job.id, "Sin conexión al servidor", videoPath)
            }
            
            // 2. Subir video
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_UPLOADING, "Subiendo video...")
            updateProgress(STATE_SUBIENDO, "Subiendo video al servidor...")
            val uploadId = subirVideo(videoPath, job.id)
            if (uploadId == null) {
                return handleError(job.id, "Error al subir el video", videoPath)
            }
            
            // 3. Monitorear procesamiento
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_PROCESSING, "Procesando video...")
            updateProgress(STATE_PROCESANDO, "Procesando video, por favor espera...")
            val resultados = esperarResultado(uploadId, job.id)
            if (resultados == null) {
                return handleError(job.id, "Error en el procesamiento", videoPath)
            }
            
            // 4. Descargar resultados
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_DOWNLOADING, "Descargando archivos...")
            updateProgress(STATE_DESCARGANDO, "Descargando archivos traducidos...")
            val esMalTraducido = descargarResultados(resultados, job.id, videoPath)
            
            // 5. Completar trabajo
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_COMPLETED, "Traducción completada")
            updateProgress(STATE_COMPLETADO, "¡Traducción completada!")
            
            if (esMalTraducido) {
                AppNotificationManager.showSuccessNotification(appContext, 
                    "Traducción con advertencia", 
                    "Tu video fue procesado pero requiere revisión", true)
            } else {
                AppNotificationManager.showSuccessNotification(appContext, 
                    "¡Traducción lista!", 
                    "Tu video fue procesado correctamente")
            }
            
            Log.i(TAG, "Procesamiento completado exitosamente")
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante el procesamiento: ${e.message}", e)
            return handleError(job.id, "Error inesperado: ${e.message}", videoPath)
        }
    }

    private suspend fun verificarConectividad(): Boolean {
        return try {
            val conectividadHelper = ConectividadHelper(appContext)
            val estadoServidor = conectividadHelper.verificarServidorConReintentos { mensaje ->
                // Usar runBlocking temporalmente para llamar suspend function desde callback
                runBlocking { updateProgress(STATE_CONECTANDO, mensaje) }
            }
            estadoServidor.esDisponible
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando conectividad: ${e.message}")
            false
        }
    }

    private suspend fun subirVideo(videoPath: String, jobId: String): String? {
        return try {
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e(TAG, "Archivo de video no existe: $videoPath")
                return null
            }
            
            val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)
            
            updateProgress(STATE_SUBIENDO, "Enviando ${videoFile.name}...")
            
            val response = ApiCliente.instance.procesarVideo(body)
            
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                
                // Validar que tengamos URLs válidas
                if (data.video_url.isNullOrBlank() || data.audio_url.isNullOrBlank() || data.texto_url.isNullOrBlank()) {
                    Log.e(TAG, "Respuesta del servidor incompleta")
                    return null
                }
                
                // Generar uploadId único para este trabajo
                val uploadId = "upload_${jobId}_${System.currentTimeMillis()}"
                
                // Guardar URLs en sistema de persistencia
                jobManager.saveJobUploadInfo(jobId, uploadId, data.video_url!!, data.audio_url!!, data.texto_url!!)
                
                Log.i(TAG, "Video subido exitosamente. Upload ID: $uploadId")
                return uploadId
                
            } else {
                Log.e(TAG, "Error del servidor: ${response.code()} - ${response.message()}")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al subir video: ${e.message}")
            null
        }
    }

    private suspend fun esperarResultado(uploadId: String, jobId: String): RespuestaProcesamiento? {
        return try {
            // Recuperar los datos guardados del job manager
            val job = jobManager.getJob(jobId)
            job?.resultUrls?.let { urls ->
                RespuestaProcesamiento(
                    video_url = urls.videoUrl,
                    audio_url = urls.audioUrl,
                    texto_url = urls.textUrl
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error esperando resultado: ${e.message}")
            null
        }
    }

    private suspend fun descargarResultados(data: RespuestaProcesamiento, jobId: String, videoPath: String): Boolean {
        return try {
            val storageManager = VideoStorageManager(appContext)
            val fecha = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val sessionName = "Sesion_$fecha"
            
            // Guardar nombre de sesión en persistencia
            jobManager.saveJobSessionName(jobId, sessionName)
            
            Log.d(TAG, "Iniciando descarga de archivos...")
            
            // Descargar video
            updateProgress(STATE_DESCARGANDO, "Descargando video traducido...")
            val videoInfo = guardarArchivoSeguro(
                storageManager,
                ApiCliente.urlAbsoluta(data.video_url!!),
                sessionName,
                "Video_$fecha.mp4",
                "video/mp4"
            )
            
            // Descargar audio
            updateProgress(STATE_DESCARGANDO, "Descargando audio...")
            val audioInfo = guardarArchivoSeguro(
                storageManager,
                ApiCliente.urlAbsoluta(data.audio_url!!),
                sessionName,
                "audio_traducido.mp3",
                "audio/mp3"
            )
            
            // Descargar texto
            updateProgress(STATE_DESCARGANDO, "Descargando transcripción...")
            val textoInfo = guardarArchivoSeguro(
                storageManager,
                ApiCliente.urlAbsoluta(data.texto_url!!),
                sessionName,
                "transcripcion.txt",
                "text/plain"
            )
            
            // Verificar contenido del texto
            val contenidoTexto = textoInfo.privateFile.readText(Charsets.UTF_8).trim()
            val esMalTraducido = contenidoTexto.isEmpty() || 
                               contenidoTexto.equals("Sin detecciones válidas.", ignoreCase = true) ||
                               contenidoTexto.equals("Sin detecciones validas.", ignoreCase = true) ||
                               contenidoTexto.contains("No se detectaron", ignoreCase = true)
            
            if (esMalTraducido) {
                Log.w(TAG, "Video marcado como mal traducido: '$contenidoTexto'")
                val videoFile = File(videoPath)
                VideoTranslationStatusHelper.marcarVideoComoMalTraducido(videoFile)
            }
            
            Log.i(TAG, "Archivos descargados exitosamente en: ${storageManager.getSessionPrivateDir(sessionName).absolutePath}")
            return esMalTraducido
            
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando resultados: ${e.message}")
            throw e
        }
    }

    private suspend fun guardarArchivoSeguro(
        storageManager: VideoStorageManager,
        url: String,
        sessionName: String,
        fileName: String,
        mimeType: String
    ): VideoStorageManager.SavedFileInfo {
        Log.d(TAG, "Descargando $fileName desde: $url")
        
        val request = okhttp3.Request.Builder().url(url).build()
        val response = ApiCliente.httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Error al descargar $fileName: ${response.code}")
        }
        
        val savedFileInfo = response.body?.byteStream()?.use { inputStream ->
            storageManager.saveFileWithGalleryReference(
                inputStream = inputStream,
                fileName = fileName,
                mimeType = mimeType,
                sessionFolder = sessionName
            )
        } ?: throw Exception("No se pudo obtener el contenido de $fileName")
        
        if (!savedFileInfo.privateFile.exists() || savedFileInfo.privateFile.length() == 0L) {
            throw Exception("El archivo $fileName está vacío o corrupto")
        }
        
        Log.d(TAG, "Archivo guardado: ${savedFileInfo.privateFile.absolutePath} (${savedFileInfo.privateFile.length()} bytes)")
        return savedFileInfo
    }

    private suspend fun updateProgress(state: String, message: String) {
        Log.d(TAG, "Estado: $state - $message")
        
        setProgress(workDataOf(
            PROGRESS_STATE to state,
            PROGRESS_MESSAGE to message
        ))
        
        // Actualizar foreground info con nueva notificación
        setForeground(createForegroundInfo(message, state))
    }

    private fun createForegroundInfo(message: String, state: String): ForegroundInfo {
        val title = when (state) {
            STATE_CONECTANDO -> "Conectando..."
            STATE_SUBIENDO -> "Subiendo video"
            STATE_PROCESANDO -> "Procesando"
            STATE_DESCARGANDO -> "Descargando"
            else -> "Manos Que Hablan"
        }
        
        val notification = AppNotificationManager.createProcessingNotification(appContext, title, message)
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private suspend fun handleError(jobId: String, errorMessage: String, videoPath: String): Result {
        Log.e(TAG, "Error en procesamiento: $errorMessage")
        
        // Actualizar estado en persistencia
        jobManager.updateJobState(jobId, VideoProcessingJobManager.STATE_FAILED, errorMessage = errorMessage)
        
        // Marcar video con error
        val videoFile = File(videoPath)
        VideoTranslationStatusHelper.marcarVideoConErrorServidor(videoFile)
        
        // Mostrar notificación de error
        AppNotificationManager.showErrorNotification(appContext, "Error en traducción", errorMessage)
        
        updateProgress(STATE_ERROR, errorMessage)
        
        return Result.retry()
    }
}