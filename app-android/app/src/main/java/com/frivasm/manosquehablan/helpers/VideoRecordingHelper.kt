package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import com.frivasm.manosquehablan.helpers.ExposureControlHelper
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
    private val exposureControlHelper = ExposureControlHelper(context, binding.previewView, cameraExecutor)


    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingStopped: (() -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null
    
    init {
        // Configurar tipo de cámara inicial (frontal por defecto)
        Log.d("VideoRecording", "INIT: Configurando cámara frontal por defecto")
    }
    
    fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            
            // Configuración OPTIMIZADA para lenguaje de señas HD:
            // - Resolución: 1280×720 (HD) - calidad necesaria para ver señas claramente  
            // - Códec: H.264 (AVC) - nativo en CameraX
            // - Bitrate CONTROLADO: usar SOLO HD con fallback hacia abajo
            
            // CONFIGURACIÓN ESTRICTA: Solo HD, nunca calidades superiores
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.HD), // SOLO HD (720p), sin Quality.FHD ni Quality.UHD
                FallbackStrategy.lowerQualityThan(Quality.HD) // Si no puede HD, usar SD (nunca 4K)
            )
            
            // Configuración optimizada para lenguaje de señas
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setTargetVideoEncodingBitRate(1_500_000) // 1.5 Mbps para HD 720p
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            camSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                provider.unbindAll()

                // Vincular preview y videoCapture
                camera = provider.bindToLifecycle(lifecycleOwner, camSelector, preview, videoCapture)
                
                // Configurar control de exposición con la cámara
                camera?.let { 
                    // IMPORTANTE: Configurar la cámara PRIMERO, luego el tipo de cámara para que funcione el brillo automático
                    Log.d("VideoRecording", "CÁMARA LISTA: Configurando exposición para ${if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONTAL" else "TRASERA"}")
                    Log.d("VideoRecording", "CALIDAD CONFIGURADA: HD 720p (1.5 Mbps bitrate), optimizado para lenguaje de señas")
                    
                    // Verificar qué calidades están disponibles usando la nueva API
                    try {
                        val cameraInfo = it.cameraInfo
                        val videoCapabilities = Recorder.getVideoCapabilities(cameraInfo)
                        val supportedQualities = videoCapabilities.getSupportedQualities(DynamicRange.SDR)
                        Log.i("VideoRecording", "Calidades soportadas: ${supportedQualities.joinToString(", ")}")
                    } catch (e: Exception) {
                        Log.w("VideoRecording", "No se pudo obtener información de calidades: ${e.message}")
                    }
                    
                    exposureControlHelper.setCamera(it)
                    exposureControlHelper.setFrontCamera(lensFacing == CameraSelector.LENS_FACING_FRONT)
                    
                    Log.i("VideoRecording", "� Cámara configurada - medición manual disponible al tocar pantalla")
                }
                
            } catch (e: Exception) {
                Log.e("VideoRecording", "Error al inicializar cámara: ${e.message}")
                onCameraError?.invoke("Error al inicializar la cámara: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun iniciarGrabacion() {
        try {
            // Verificar que videoCapture esté inicializado
            if (videoCapture == null) {
                Log.e("VideoRecording", "Error: videoCapture no está inicializado")
                onRecordingError?.invoke("Error: Cámara no inicializada")
                return
            }

            currentTempFile = createTempFile()
            if (currentTempFile == null) {
                Log.e("VideoRecording", "Error: No se pudo crear archivo temporal")
                onRecordingError?.invoke("Error al crear archivo de grabación")
                return
            }

            val outputOptions = FileOutputOptions.Builder(currentTempFile!!).build()

            recording = videoCapture!!.output.prepareRecording(context, outputOptions).apply {
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
        
        // Actualizar inmediatamente el tipo de cámara en el exposureControlHelper
        Log.d("VideoRecording", "ROTANDO CÁMARA: Cambiando a ${if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONTAL" else "TRASERA"}")
        exposureControlHelper.setFrontCamera(lensFacing == CameraSelector.LENS_FACING_FRONT)
        
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
