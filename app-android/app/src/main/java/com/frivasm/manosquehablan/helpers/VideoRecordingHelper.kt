package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import java.io.File
import java.util.concurrent.ExecutorService

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
    
    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingStopped: (() -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null
    
    fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            camSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, camSelector, preview, videoCapture)
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
                        onRecordingStarted?.invoke()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            onRecordingStopped?.invoke()
                        } else {
                            // Solo mostrar error si no es una cancelación intencional
                            val errorMessage = event.error?.toString() ?: "Error al grabar video"
                            if (!errorMessage.contains("cancelled", ignoreCase = true) && 
                                !errorMessage.contains("cancelado", ignoreCase = true)) {
                                onRecordingError?.invoke("Error al grabar video")
                            } else {
                                Log.d("VideoRecording", "Grabación cancelada intencionalmente")
                            }
                        }
                        recording = null
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
            // Si hay una grabación activa, detenerla de forma silenciosa
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
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("VideoRecording", "Cleanup completado: ${e.message}")
        }
    }
}
