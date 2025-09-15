package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import java.io.File
import java.util.concurrent.ExecutorService

@ExperimentalCamera2Interop
class VideoRecordingHelper(
    private val context: Context,
    private val binding: ActivityGrabarVideoBinding,
    private val lifecycleOwner: LifecycleOwner,
    private val cameraExecutor: ExecutorService
) {
    
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var camSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var currentTempFile: File? = null
    private var isPaused = false
    private var camera: Camera? = null
    
    // Control de exposición inteligente
    val exposureControlHelper = ExposureControlHelper(context, binding.previewView, cameraExecutor)
    
    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingStopped: (() -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null
    
    init {
        // Configurar tipo de cámara inicial (frontal por defecto)
        Log.d("VideoRecording", "INIT: Configurando cámara frontal por defecto")
        exposureControlHelper.setFrontCamera(lensFacing == CameraSelector.LENS_FACING_FRONT)
        
        // Configurar callback para notificaciones de exposición
        exposureControlHelper.onExposureChanged = { luma, evCompensation, torchEnabled ->
            val supportInfo = if (exposureControlHelper.isExposureSupported()) "EV" else "Solo medición"
            val torchInfo = if (exposureControlHelper.isTorchSupported()) "linterna" else "sin linterna"
            Log.d("VideoRecording", "Exposición [$supportInfo,$torchInfo]: luma=${String.format("%.3f", luma)}, EV=$evCompensation, torch=$torchEnabled")
        }
    }
    
    fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            
            // Configuración específica para calidad HD consistente:
            // - Resolución: 1280×720 (HD 720p)
            // - FPS: 30 cuadros por segundo
            // - Códec: H.264 (AVC) - nativo en CameraX
            // - Bitrate: 2.0-2.5 Mbps (manejado automáticamente por Quality.HD)
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.HD, Quality.FHD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
            )
            
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            camSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                provider.unbindAll()
                
                // Vincular preview, videoCapture y análisis de imagen para exposición
                val imageAnalysis = exposureControlHelper.getImageAnalysis()
                if (imageAnalysis != null) {
                    camera = provider.bindToLifecycle(lifecycleOwner, camSelector, preview, videoCapture, imageAnalysis)
                } else {
                    camera = provider.bindToLifecycle(lifecycleOwner, camSelector, preview, videoCapture)
                }
                
                // Configurar control de exposición con la cámara
                camera?.let { 
                    // IMPORTANTE: Configurar tipo de cámara ANTES de setCamera para evitar race condition
                    Log.d("VideoRecording", "CÁMARA LISTA: Configurando exposición para ${if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONTAL" else "TRASERA"}")
                    Log.d("VideoRecording", "CALIDAD CONFIGURADA: HD 720p, 30fps, H.264, ~2.0-2.5 Mbps")
                    exposureControlHelper.setFrontCamera(lensFacing == CameraSelector.LENS_FACING_FRONT)
                    exposureControlHelper.setCamera(it) 
                }
                
            } catch (e: Exception) {
                // Solo log, sin toast
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun iniciarGrabacion() {
        try {
            currentTempFile = createTempFile()
            val outputOptions = FileOutputOptions.Builder(currentTempFile!!).build()
            val currentCapture = videoCapture ?: return
            
            recording = currentCapture.output.prepareRecording(context, outputOptions).apply {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        // Notificar al control de exposición que inició la grabación
                        exposureControlHelper.onRecordingStarted()
                        onRecordingStarted?.invoke()
                    }
                    is VideoRecordEvent.Finalize -> {
                        // Notificar al control de exposición que terminó la grabación
                        exposureControlHelper.onRecordingStopped()
                        
                        // Log telemetría del clip si hay datos
                        val tempFilePath = currentTempFile?.name ?: "unknown_clip"
                        exposureControlHelper.logTelemetryForClip(tempFilePath)
                        
                        if (!event.hasError()) {
                            onRecordingStopped?.invoke()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error al iniciar grabación: ${e.message}")
            onRecordingError?.invoke("Error al iniciar grabación")
        }
    }
    
    fun detenerGrabacion() {
        try {
            recording?.let { currentRecording ->
                try {
                    currentRecording.stop()
                } catch (e: Exception) {
                    // No mostrar error si se cancela intencionalmente
                    Log.d("VideoRecording", "Grabación detenida intencionalmente")
                }
            }
            recording = null
            isPaused = false
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("VideoRecording", "Grabación detenida: ${e.message}")
        }
    }
    
    fun pausarGrabacion() {
        try {
            recording?.pause()
            isPaused = true
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error al pausar grabación: ${e.message}")
            onRecordingError?.invoke("Error al pausar grabación")
        }
    }
    
    fun reanudarGrabacion() {
        try {
            recording?.resume()
            isPaused = false
        } catch (e: Exception) {
            Log.e("VideoRecording", "Error al reanudar grabación: ${e.message}")
            onRecordingError?.invoke("Error al reanudar grabación")
        }
    }
    
    fun rotarCamara() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        iniciarCamara()
    }
    
    fun isRecording(): Boolean = recording != null
    
    fun isPaused(): Boolean = isPaused
    
    private fun createTempFile(): File {
        return File.createTempFile("temp_video", ".mp4", context.cacheDir)
    }
    
    fun getRecordingPath(): String? {
        return currentTempFile?.absolutePath
    }
    
    fun cleanup() {
        try {
            // Limpiar control de exposición
            exposureControlHelper.cleanup()
            
            // Detener grabación de forma silenciosa
            recording?.let { currentRecording ->
                try {
                    currentRecording.stop()
                } catch (e: Exception) {
                    // No mostrar error si se cancela intencionalmente
                    Log.d("VideoRecording", "Grabación cancelada durante cleanup")
                }
            }
            recording = null
            currentTempFile = null
            isPaused = false
            camera = null
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("VideoRecording", "Cleanup completado: ${e.message}")
        }
    }
}
