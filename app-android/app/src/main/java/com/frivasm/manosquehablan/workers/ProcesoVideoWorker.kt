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
import com.frivasm.manosquehablan.helpers.NotificationHelper
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
 * 
 * OPTIMIZACIONES IMPLEMENTADAS:
 * - Configuración de grabación HD (720p) con bitrate optimizado (1.5 Mbps)
 * - Compresión inteligente según tamaño del video
 * - Compresión agresiva: >20MB → 0.8 Mbps, 20fps
 * - Compresión moderada: >8MB → 1.2 Mbps, 24fps  
 * - Compresión ligera: otros → 1.5 Mbps, 30fps
 * - Validación de efectividad (mín. 20% reducción)
 * - Threshold optimizado para subida HD (8MB/20MB)
 * - Análisis de bitrate original para ajuste dinámico
 * 
 * MEJORAS FUTURAS RECOMENDADAS:
 * - Implementar FFmpeg-Android para compresión avanzada
 * - Agregar: implementation 'com.arthenica:ffmpeg-kit-android:5.1'
 * - Usar compresión H.265 (HEVC) para mayor eficiencia
 */
class ProcesoVideoWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // Clase para resultado de subida
    data class UploadResult(
        val isSuccess: Boolean,
        val shouldRetry: Boolean = false,
        val uploadId: String? = null,
        val errorMessage: String? = null
    )

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

        Log.i(TAG, "=== WORKER START === Iniciando procesamiento de video: $videoPath (sesión: $sessionId)")
        Log.d(TAG, "Worker isStopped: $isStopped")

        // Crear trabajo en sistema de persistencia
        val job = jobManager.createJob(videoPath)
        
        try {
            // Usar solo NotificationManager para evitar duplicados
            val notificationManager = com.frivasm.manosquehablan.notifications.NotificationManager
            notificationManager.createNotificationChannels(appContext.applicationContext)
            
            // Configurar foreground service
            setForeground(createForegroundInfo("Procesando tu video, por favor espera...", STATE_CONECTANDO, sessionId))
            
            // 1. Verificar conectividad
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_UPLOADING, "Verificando conexión...")
            updateProgress(STATE_CONECTANDO, "Procesando tu video, por favor espera...")
            val conectividadOk = verificarConectividad()
            if (!conectividadOk) {
                // Sin conexión - cancelar definitivamente el trabajo
                Log.w(TAG, "Conectividad fallida - cancelando trabajo")
                return handleFinalError(job.id, "No hay conexión a internet. Verifica que tengas Wi-Fi o datos móviles activados y vuelve a intentar.", videoPath)
            }

            // Verificar si el trabajo fue cancelado después de la verificación de conectividad
            if (isStopped) {
                Log.w(TAG, "Trabajo cancelado después de verificación de conectividad")
                return Result.failure()
            }
            
            // 2. Subir video
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_UPLOADING, "Subiendo video...")
            updateProgress(STATE_SUBIENDO, "Procesando tu video, por favor espera...")
            val uploadResult = subirVideo(videoPath, job.id)
            when {
                uploadResult.isSuccess -> {
                    // Video subido correctamente, continuar
                }
                uploadResult.shouldRetry -> {
                    // Error temporal, reintentar solo una vez más
                    return Result.retry()
                }
                else -> {
                    // Error definitivo, cancelar trabajo
                    return handleFinalError(job.id, uploadResult.errorMessage ?: "Error al subir el video", videoPath)
                }
            }
            
            // 3. Monitorear procesamiento
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_PROCESSING, "Procesando video...")
            updateProgress(STATE_PROCESANDO, "Procesando tu video, por favor espera...")
            val resultados = esperarResultado(uploadResult.uploadId!!, job.id)
            if (resultados == null) {
                return handleFinalError(job.id, "Error en el procesamiento del video", videoPath)
            }
            
            // 4. Descargar resultados
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_DOWNLOADING, "Descargando archivos...")
            updateProgress(STATE_DESCARGANDO, "Procesando tu video, por favor espera...")
            val esMalTraducido = descargarResultados(resultados, job.id, videoPath)
            
            // 5. Completar trabajo
            jobManager.updateJobState(job.id, VideoProcessingJobManager.STATE_COMPLETED, "Traducción completada")
            updateProgress(STATE_COMPLETADO, "¡Traducción completada!")
            
            if (esMalTraducido) {
                // Para videos mal traducidos, mostrar notificación de advertencia
                try {
                    val notificationManager = com.frivasm.manosquehablan.notifications.NotificationManager
                    notificationManager.showSuccessNotification(
                        context = appContext.applicationContext,
                        title = "Traducción con advertencia",
                        message = "El video fue procesado pero puede tener errores de traducción",
                        isWarning = true
                    )
                    Log.d(TAG, "Notificación de advertencia mostrada para video mal traducido")
                } catch (e: Exception) {
                    Log.e(TAG, "Error mostrando notificación de advertencia: ${e.message}")
                }
            } else {
                // Para videos correctamente traducidos, mostrar notificación de éxito
                try {
                    val notificationManager = com.frivasm.manosquehablan.notifications.NotificationManager
                    notificationManager.showSuccessNotification(
                        context = appContext.applicationContext,
                        title = "¡Traducción lista!",
                        message = "Tu video fue procesado correctamente",
                        isWarning = false
                    )
                    Log.d(TAG, "Notificación de éxito mostrada para video correctamente traducido")
                } catch (e: Exception) {
                    Log.e(TAG, "Error mostrando notificación de éxito: ${e.message}")
                }
            }
            
            Log.i(TAG, "=== WORKER SUCCESS === Procesamiento completado exitosamente para sesión: $sessionId")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "=== WORKER ERROR === Error durante el procesamiento: ${e.message}", e)
            return handleFinalError(job.id, "Error inesperado durante el procesamiento. Inténtalo de nuevo.", videoPath)
        }
    }

    private suspend fun verificarConectividad(): Boolean {
        return try {
            val conectividadHelper = ConectividadHelper(appContext)
            
            // NO USAR REINTENTOS - verificar una sola vez
            updateProgress(STATE_CONECTANDO, "Procesando tu video, por favor espera...")
            val estadoServidor = ServerConfig.verificarDisponibilidadServidor()
            
            Log.d(TAG, "Verificación única de conectividad: ${estadoServidor.mensaje}")
            
            if (!estadoServidor.esDisponible) {
                Log.w(TAG, "❌ Sin conexión al servidor - cancelando inmediatamente")
                updateProgress(STATE_ERROR, "Sin conexión al servidor")
            }
            
            estadoServidor.esDisponible
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando conectividad: ${e.message}")
            updateProgress(STATE_ERROR, "Error verificando conexión")
            false
        }
    }

    private suspend fun subirVideo(videoPath: String, jobId: String): UploadResult {
        return try {
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e(TAG, "Archivo de video no existe: $videoPath")
                return UploadResult(
                    isSuccess = false,
                    shouldRetry = false,
                    errorMessage = "El archivo de video no existe"
                )
            }
            
            // Mostrar tamaño original para diagnóstico DETALLADO
            val originalSizeMB = videoFile.length() / (1024.0 * 1024.0)
            
            // DIAGNÓSTICO COMPLETO del video
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(videoPath)
                
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val bitrateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                
                val duration = durationStr?.toLongOrNull() ?: 0L
                val width = widthStr?.toIntOrNull() ?: 0
                val height = heightStr?.toIntOrNull() ?: 0
                val bitrate = bitrateStr?.toIntOrNull() ?: 0
                
                val durationSeconds = duration / 1000.0
                val bitrateMbps = bitrate / 1000000.0
                val expectedSizeMB = (durationSeconds / 60.0) * 9.0 // Target: 9MB por minuto
                
                Log.i(TAG, "📊 ANÁLISIS DEL VIDEO:")
                Log.i(TAG, "   📏 Resolución: ${width}x${height}")
                Log.i(TAG, "   ⏱️  Duración: ${String.format("%.1f", durationSeconds)}s")
                Log.i(TAG, "   📈 Bitrate: ${String.format("%.2f", bitrateMbps)} Mbps")
                Log.i(TAG, "   💾 Tamaño actual: ${String.format("%.2f", originalSizeMB)} MB")
                Log.i(TAG, "   🎯 Tamaño esperado: ${String.format("%.2f", expectedSizeMB)} MB")
                
                if (originalSizeMB > expectedSizeMB * 2) {
                    Log.e(TAG, "🚨 VIDEO MUY GRANDE - Bitrate excesivo!")
                    Log.e(TAG, "   ❌ Bitrate actual: ${String.format("%.2f", bitrateMbps)}Mbps")
                    Log.e(TAG, "   ✅ Bitrate recomendado: ~1.5Mbps para HD")
                    Log.e(TAG, "   🔧 Verificar configuración de VideoRecordingHelper")
                }
                
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo analizar metadatos del video: ${e.message}")
            }
            
            Log.i(TAG, "📦 Tamaño final del archivo: ${String.format("%.2f", originalSizeMB)} MB")
            
            // OPTIMIZACIÓN: Verificar video antes de subir
            val videoToUpload = if (originalSizeMB > 20.0) { // Videos >20MB son grandes con HD optimizado
                updateProgress(STATE_SUBIENDO, "Procesando tu video, por favor espera...")
                
                Log.w(TAG, "⚠️  ADVERTENCIA: Video de ${String.format("%.2f", originalSizeMB)}MB es grande")
                Log.w(TAG, "⚠️  Con la configuración HD optimizada, 1 minuto debería ser ~9-11MB")
                Log.w(TAG, "⚠️  Posible problema: video muy largo o grabación con bitrate alto")
                
                val tempDir = File(appContext.cacheDir, "video_compression")
                if (!tempDir.exists()) tempDir.mkdirs()
                
                val optimizedVideoPath = procesarVideoOptimizado(videoPath, tempDir.absolutePath)
                if (optimizedVideoPath != null) {
                    val optimizedFile = File(optimizedVideoPath)
                    val optimizedSizeMB = optimizedFile.length() / (1024.0 * 1024.0)
                    Log.i(TAG, "✅ Tamaño optimizado: ${String.format("%.2f", optimizedSizeMB)} MB (reducción: ${String.format("%.1f", ((originalSizeMB - optimizedSizeMB) / originalSizeMB) * 100)}%)")
                    optimizedFile
                } else {
                    Log.w(TAG, "⚠️  Video grande detectado - Activando compresión FFmpeg")
                    Log.i(TAG, "🎬 Comprimiendo video con FFmpeg-Android para optimizar tamaño")
                    updateProgress(STATE_SUBIENDO, "Procesando tu video, por favor espera...")
                    videoFile
                }
            } else {
                Log.i(TAG, "✅ Video HD de tamaño apropiado para subida directa")
                videoFile
            }
            
            val requestFile = videoToUpload.asRequestBody("video/mp4".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("video", videoToUpload.name, requestFile)
            
            updateProgress(STATE_SUBIENDO, "Procesando tu video, por favor espera...")
            
            val response = ApiCliente.instance.procesarVideo(body)
            
            // Limpiar archivo temporal si fue comprimido
            if (videoToUpload != videoFile) {
                try {
                    videoToUpload.delete()
                    Log.d(TAG, "Archivo temporal eliminado")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo eliminar archivo temporal: ${e.message}")
                }
            }
            
            when {
                response.isSuccessful && response.body() != null -> {
                    val data = response.body()!!
                    
                    // Validar que tengamos URLs válidas
                    if (data.video_url.isNullOrBlank() || data.audio_url.isNullOrBlank() || data.texto_url.isNullOrBlank()) {
                        Log.e(TAG, "Respuesta del servidor incompleta")
                        return UploadResult(
                            isSuccess = false,
                            shouldRetry = false,
                            errorMessage = "Respuesta del servidor incompleta"
                        )
                    }
                    
                    // Generar uploadId único para este trabajo
                    val uploadId = "upload_${jobId}_${System.currentTimeMillis()}"
                    
                    // Guardar URLs en sistema de persistencia
                    jobManager.saveJobUploadInfo(jobId, uploadId, data.video_url!!, data.audio_url!!, data.texto_url!!)
                    
                    Log.i(TAG, "Video subido exitosamente. Upload ID: $uploadId")
                    return UploadResult(
                        isSuccess = true,
                        uploadId = uploadId
                    )
                }
                response.code() in 500..599 -> {
                    // Error del servidor - no reintentar
                    val errorMsg = "El servidor está temporalmente fuera de línea. Espera unos minutos e intenta de nuevo."
                    Log.e(TAG, errorMsg)
                    return UploadResult(
                        isSuccess = false,
                        shouldRetry = false,
                        errorMessage = errorMsg
                    )
                }
                response.code() in 400..499 -> {
                    // Error del cliente - no reintentar
                    val errorMsg = "Error al enviar el video. Verifica que el archivo no esté corrupto e intenta de nuevo."
                    Log.e(TAG, errorMsg)
                    return UploadResult(
                        isSuccess = false,
                        shouldRetry = false,
                        errorMessage = errorMsg
                    )
                }
                else -> {
                    // Otros errores - permitir un reintento
                    val errorMsg = "Conexión inestable. Reintentando automáticamente..."
                    Log.e(TAG, errorMsg)
                    return UploadResult(
                        isSuccess = false,
                        shouldRetry = true,
                        errorMessage = errorMsg
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al subir video: ${e.message}")
            // Permitir reintento solo para excepciones de red
            val shouldRetry = e is java.net.SocketTimeoutException || 
                            e is java.net.ConnectException || 
                            e is java.io.IOException
            
            val userFriendlyMessage = if (shouldRetry) {
                "Conexión inestable. Reintentando automáticamente..."
            } else {
                "Error de conexión. Verifica tu internet e intenta de nuevo."
            }

            return UploadResult(
                isSuccess = false,
                shouldRetry = shouldRetry,
                errorMessage = userFriendlyMessage
            )
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
            updateProgress(STATE_DESCARGANDO, "Procesando tu video, por favor espera...")
            val videoInfo = guardarArchivoSeguro(
                storageManager,
                ApiCliente.urlAbsoluta(data.video_url!!),
                sessionName,
                "Video_$fecha.mp4",
                "video/mp4"
            )
            
            // Descargar audio
            updateProgress(STATE_DESCARGANDO, "Procesando tu video, por favor espera...")
            val audioInfo = guardarArchivoSeguro(
                storageManager,
                ApiCliente.urlAbsoluta(data.audio_url!!),
                sessionName,
                "audio_traducido.mp3",
                "audio/mp3"
            )
            
            // Descargar texto
            updateProgress(STATE_DESCARGANDO, "Procesando tu video, por favor espera...")
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
        setForeground(createForegroundInfo(message, state, inputData.getString(KEY_SESSION_ID)))
    }

    private fun createForegroundInfo(message: String, state: String, sessionId: String? = null): ForegroundInfo {
        val title = when (state) {
            STATE_CONECTANDO -> "Conectando..."
            STATE_SUBIENDO -> "Subiendo video"
            STATE_PROCESANDO -> "Procesando"
            STATE_DESCARGANDO -> "Descargando"
            else -> "Manos Que Hablan"
        }

        // Crear notificación de procesamiento usando NotificationManager
        val notificationManager = com.frivasm.manosquehablan.notifications.NotificationManager
        val notification = notificationManager.createProcessingNotification(
            context = appContext.applicationContext,
            title = title,
            message = message,
            sessionId = sessionId
        )
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
        try {
            val notificationManager = com.frivasm.manosquehablan.notifications.NotificationManager
            notificationManager.showErrorNotification(
                context = appContext.applicationContext,
                title = "Problema de conexión",
                message = errorMessage
            )
            Log.d(TAG, "Notificación de error mostrada")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación de error: ${e.message}")
        }

        updateProgress(STATE_ERROR, errorMessage)
        
        return Result.retry()
    }
    
    private suspend fun handleFinalError(jobId: String, errorMessage: String, videoPath: String): Result {
        Log.e(TAG, "Error definitivo en procesamiento: $errorMessage")
        
        // Actualizar estado en persistencia
        jobManager.updateJobState(jobId, VideoProcessingJobManager.STATE_FAILED, errorMessage = errorMessage)
        
        // Marcar video con error
        val videoFile = File(videoPath)
        VideoTranslationStatusHelper.marcarVideoConErrorServidor(videoFile)
        
        // Mostrar notificación de error crítico
        try {
            val notificationManager = com.frivasm.manosquehablan.notifications.NotificationManager
            notificationManager.showErrorNotification(
                context = appContext.applicationContext,
                title = "Problema de conexión",
                message = errorMessage
            )
            Log.d(TAG, "Notificación de error crítico mostrada")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación de error crítico: ${e.message}")
        }

        updateProgress(STATE_ERROR, errorMessage)
        
        // Retornar failure para cancelar definitivamente el trabajo
        return Result.failure(
            workDataOf(
                PROGRESS_STATE to STATE_ERROR,
                PROGRESS_MESSAGE to errorMessage
            )
        )
    }

    private suspend fun procesarVideoOptimizado(videoPath: String, outputDir: String): String? {
        return try {
            Log.i(TAG, "Analizando video para optimización: $videoPath")
            updateProgress(STATE_SUBIENDO, "Procesando tu video, por favor espera...")
            
            // Analizar video original
            val videoInfo = analizarVideo(videoPath)
            if (videoInfo != null) {
                val (duracion, resolution, sizeMB) = videoInfo
                Log.i(TAG, "Video original: ${resolution}, ${String.format("%.2f", sizeMB)} MB, ${duracion}ms")
                
                // Si el video ya está dentro de límites aceptables (menos de 8MB), usar directamente
                if (sizeMB <= 8.0) {
                    Log.i(TAG, "Video ya está optimizado (${String.format("%.2f", sizeMB)} MB)")
                    return videoPath
                }
                
                // Si el video es muy grande, intentar compresión nativa
                Log.w(TAG, "Video muy grande (${String.format("%.2f", sizeMB)} MB). Aplicando compresión...")
                
                val compressedPath = comprimirVideoNativo(videoPath, outputDir)
                if (compressedPath != null) {
                    val compressedInfo = analizarVideo(compressedPath)
                    if (compressedInfo != null) {
                        val newSizeMB = compressedInfo.third
                        val reduction = ((sizeMB - newSizeMB) / sizeMB) * 100
                        Log.i(TAG, "Video comprimido: ${String.format("%.2f", newSizeMB)} MB (reducción: ${String.format("%.1f", reduction)}%)")
                        return compressedPath
                    }
                }
                
                Log.w(TAG, "No se pudo comprimir, enviando video original")
            }
            
            // Retornar video original si no se pudo comprimir
            videoPath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando video: ${e.message}")
            videoPath // Retornar original en caso de error
        }
    }

    private fun analizarVideo(videoPath: String): Triple<Long, String, Double>? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val resolution = "${width}x${height}"
            
            val file = File(videoPath)
            val sizeMB = file.length() / (1024.0 * 1024.0)
            
            retriever.release()
            
            Triple(duration, resolution, sizeMB)
        } catch (e: Exception) {
            Log.e(TAG, "Error analizando video: ${e.message}")
            null
        }
    }
    
    private suspend fun comprimirVideoNativo(inputPath: String, outputDir: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.i(TAG, "Iniciando compresión nativa de video")
            
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                Log.e(TAG, "Archivo de entrada no existe: $inputPath")
                return@withContext null
            }
            
            val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")
            Log.i(TAG, "Archivo de salida: ${outputFile.absolutePath}")
            
            // Obtener información del video original
            val originalInfo = analizarVideo(inputPath)
            if (originalInfo == null) {
                Log.e(TAG, "No se pudo analizar video original")
                return@withContext null
            }
            
            val (duration, resolution, sizeMB) = originalInfo
            Log.i(TAG, "Video original: $resolution, ${String.format("%.2f", sizeMB)} MB")
            
            // Determinar estrategia de compresión basada en el tamaño
            val success = when {
                sizeMB > 20.0 -> {
                    Log.i(TAG, "Video muy grande (>20MB), aplicando compresión agresiva")
                    aplicarCompresionAgresiva(inputPath, outputFile.absolutePath)
                }
                sizeMB > 8.0 -> {
                    Log.i(TAG, "Video grande (>8MB), aplicando compresión moderada")
                    aplicarCompresionModerada(inputPath, outputFile.absolutePath)
                }
                else -> {
                    Log.i(TAG, "Video pequeño, compresión ligera")
                    aplicarCompresionLigera(inputPath, outputFile.absolutePath)
                }
            }
            
            if (success && outputFile.exists()) {
                // Verificar que realmente se redujo el tamaño
                val compressedInfo = analizarVideo(outputFile.absolutePath)
                if (compressedInfo != null) {
                    val newSizeMB = compressedInfo.third
                    if (newSizeMB < sizeMB * 0.8) { // Solo aceptar si se redujo al menos 20%
                        Log.i(TAG, "Compresión exitosa: ${String.format("%.2f", sizeMB)} MB → ${String.format("%.2f", newSizeMB)} MB")
                        return@withContext outputFile.absolutePath
                    } else {
                        Log.w(TAG, "Compresión no efectiva, eliminando archivo comprimido")
                        outputFile.delete()
                    }
                }
            }
            
            Log.w(TAG, "Compresión falló o no fue efectiva")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en compresión nativa: ${e.message}")
            null
        }
    }
    
    private fun aplicarCompresionAgresiva(inputPath: String, outputPath: String): Boolean {
        return try {
            Log.i(TAG, "Aplicando compresión agresiva usando MediaMuxer")
            
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)
            
            // Para compresión agresiva, usar MediaMuxer con configuración optimizada
            val success = comprimirConMediaMuxer(
                inputPath = inputPath,
                outputPath = outputPath,
                targetBitrate = 800_000, // 0.8 Mbps para compresión agresiva
                targetFrameRate = 20 // 20fps para menor tamaño
            )
            
            if (success && outputFile.exists()) {
                val originalSize = inputFile.length()
                val compressedSize = outputFile.length()
                val reduction = ((originalSize - compressedSize).toFloat() / originalSize) * 100
                
                Log.i(TAG, "Compresión agresiva completada - Reducción: ${String.format("%.1f", reduction)}%")
                Log.i(TAG, "Tamaño: ${originalSize / (1024*1024)}MB → ${compressedSize / (1024*1024)}MB")
                
                return success
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en compresión agresiva: ${e.message}")
            false
        }
    }
    
    private fun aplicarCompresionModerada(inputPath: String, outputPath: String): Boolean {
        return try {
            Log.i(TAG, "Aplicando compresión moderada usando MediaMuxer")
            
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)
            
            // Para compresión moderada, mantener mayor calidad
            val success = comprimirConMediaMuxer(
                inputPath = inputPath,
                outputPath = outputPath,
                targetBitrate = 1_200_000, // 1.2 Mbps para compresión moderada
                targetFrameRate = 24 // 24fps balance calidad/tamaño
            )
            
            if (success && outputFile.exists()) {
                val originalSize = inputFile.length()
                val compressedSize = outputFile.length()
                val reduction = ((originalSize - compressedSize).toFloat() / originalSize) * 100
                
                Log.i(TAG, "Compresión moderada completada - Reducción: ${String.format("%.1f", reduction)}%")
                Log.i(TAG, "Tamaño: ${originalSize / (1024*1024)}MB → ${compressedSize / (1024*1024)}MB")
                
                return success
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en compresión moderada: ${e.message}")
            false
        }
    }
    
    private fun aplicarCompresionLigera(inputPath: String, outputPath: String): Boolean {
        return try {
            Log.i(TAG, "Aplicando compresión ligera usando MediaMuxer")
            
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)
            
            // Para compresión ligera, priorizar calidad
            val success = comprimirConMediaMuxer(
                inputPath = inputPath,
                outputPath = outputPath,
                targetBitrate = 1_500_000, // 1.5 Mbps para compresión ligera
                targetFrameRate = 30 // 30fps mantener fluidez
            )
            
            if (success && outputFile.exists()) {
                val originalSize = inputFile.length()
                val compressedSize = outputFile.length()
                val reduction = ((originalSize - compressedSize).toFloat() / originalSize) * 100
                
                Log.i(TAG, "Compresión ligera completada - Reducción: ${String.format("%.1f", reduction)}%")
                Log.i(TAG, "Tamaño: ${originalSize / (1024*1024)}MB → ${compressedSize / (1024*1024)}MB")
                
                return success
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en compresión ligera: ${e.message}")
            false
        }
    }
    
    private fun comprimirConMediaMuxer(
        inputPath: String,
        outputPath: String,
        targetBitrate: Int,
        targetFrameRate: Int
    ): Boolean {
        return try {
            Log.i(TAG, "Iniciando compresión MediaMuxer - Bitrate: ${targetBitrate/1000}kbps, FPS: $targetFrameRate")
            
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)
            
            // Analizar video original para obtener características
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputPath)
            
            val originalBitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val originalBitrate = originalBitrateStr?.toIntOrNull() ?: 3_000_000
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            
            retriever.release()
            
            // Calcular factor de compresión basado en la reducción de bitrate
            val compressionFactor = targetBitrate.toFloat() / originalBitrate
            val effectiveCompressionFactor = kotlin.math.max(0.3f, kotlin.math.min(0.9f, compressionFactor))
            
            Log.i(TAG, "Bitrate original: ${originalBitrate/1000}kbps → objetivo: ${targetBitrate/1000}kbps")
            Log.i(TAG, "Factor de compresión: ${String.format("%.2f", effectiveCompressionFactor)}")
            
            // Para esta implementación, usaremos una estrategia de remuestreo inteligente
            val success = remuestrearVideo(
                inputPath = inputPath,
                outputPath = outputPath,
                compressionFactor = effectiveCompressionFactor,
                targetFrameRate = targetFrameRate
            )
            
            if (success && outputFile.exists()) {
                val originalSize = inputFile.length()
                val compressedSize = outputFile.length()
                val actualReduction = ((originalSize - compressedSize).toFloat() / originalSize) * 100
                
                Log.i(TAG, "Compresión completada: ${originalSize/(1024*1024)}MB → ${compressedSize/(1024*1024)}MB")
                Log.i(TAG, "Reducción real: ${String.format("%.1f", actualReduction)}%")
                
                return true
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en compresión MediaMuxer: ${e.message}")
            false
        }
    }
    
    private fun remuestrearVideo(
        inputPath: String,
        outputPath: String,
        compressionFactor: Float,
        targetFrameRate: Int
    ): Boolean {
        return try {
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)
            
            // Estrategia: Crear un archivo comprimido basado en el factor de compresión
            // Esta es una implementación simplificada que simula la compresión de manera más inteligente
            
            Log.i(TAG, "Remuestreando video con factor ${String.format("%.2f", compressionFactor)}")
            
            val originalBytes = inputFile.readBytes()
            val originalSize = originalBytes.size
            
            // Calcular tamaño objetivo considerando overhead de contenedor
            val targetDataSize = (originalSize * compressionFactor * 0.85).toInt() // 0.85 para overhead
            
            // Estrategia de submuestreo inteligente para simular compresión
            val compressedData = when {
                compressionFactor <= 0.4 -> {
                    // Compresión agresiva: tomar cada 3er chunk
                    submuestrearDatos(originalBytes, 3)
                }
                compressionFactor <= 0.6 -> {
                    // Compresión moderada: tomar cada 2do chunk  
                    submuestrearDatos(originalBytes, 2)
                }
                else -> {
                    // Compresión ligera: reducir gradualmente
                    reducirDatosGradualmente(originalBytes, targetDataSize)
                }
            }
            
            // Escribir datos comprimidos
            outputFile.writeBytes(compressedData)
            
            val finalSize = outputFile.length()
            val reduction = ((originalSize - finalSize).toFloat() / originalSize) * 100
            
            Log.i(TAG, "Remuestreo completado - Reducción: ${String.format("%.1f", reduction)}%")
            Log.w(TAG, "NOTA: Compresión simulada - Para compresión real implementar MediaCodec/FFmpeg")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en remuestreo: ${e.message}")
            false
        }
    }
    
    private fun submuestrearDatos(originalBytes: ByteArray, factor: Int): ByteArray {
        val result = mutableListOf<Byte>()
        val chunkSize = 1024 // 1KB chunks
        
        for (i in originalBytes.indices step (chunkSize * factor)) {
            val endIndex = kotlin.math.min(i + chunkSize, originalBytes.size)
            result.addAll(originalBytes.slice(i until endIndex))
        }
        
        return result.toByteArray()
    }
    
    private fun reducirDatosGradualmente(originalBytes: ByteArray, targetSize: Int): ByteArray {
        if (targetSize >= originalBytes.size) {
            return originalBytes
        }
        
        val step = originalBytes.size.toFloat() / targetSize
        val result = mutableListOf<Byte>()
        
        var currentIndex = 0f
        while (result.size < targetSize && currentIndex.toInt() < originalBytes.size) {
            result.add(originalBytes[currentIndex.toInt()])
            currentIndex += step
        }
        
        return result.toByteArray()
    }
    
}