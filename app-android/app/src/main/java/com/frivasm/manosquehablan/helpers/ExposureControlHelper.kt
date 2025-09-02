package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import kotlin.math.max
import kotlin.math.min

@ExperimentalCamera2Interop
class ExposureControlHelper(
    private val context: Context,
    private val previewView: PreviewView,
    private val cameraExecutor: ExecutorService
) {
    
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var camera2CameraControl: Camera2CameraControl? = null
    private var camera2CameraInfo: Camera2CameraInfo? = null
    
    // SharedPreferences para persistir configuración del usuario
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("exposure_settings", Context.MODE_PRIVATE)
    
    // Verificación de soporte de funciones
    private var isExposureCompensationSupported = false
    private var isTorchSupported = false
    
    // Parámetros de exposición según especificaciones
    private val TARGET_LUMA_MIN = 0.50f
    private val TARGET_LUMA_MAX = 0.58f
    private val HYSTERESIS_MIN = 0.47f
    private val HYSTERESIS_MAX = 0.60f
    private val CRITICAL_LOW_LUMA = 0.45f
    private val CRITICAL_HIGH_LUMA = 0.62f
    private val TORCH_ENABLE_THRESHOLD = 0.35f
    private val TORCH_DISABLE_THRESHOLD = 0.48f
    private val RECORDING_LOCK_MIN = 0.48f
    private val RECORDING_LOCK_MAX = 0.60f
    private val CLIPPING_THRESHOLD = 0.015f // 1.5%
    
    // Rango de compensación EV
    private val MIN_EV_COMPENSATION = -2
    private val MAX_EV_COMPENSATION = 2
    
    // Estado actual
    private var currentEvCompensation = 0
    private var isTorchEnabled = false
    private var isAeLocked = false
    private var isRecording = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // Análisis de imagen para medición de luma
    private var imageAnalysis: ImageAnalysis? = null
    private var currentLuma = 0.5f
    private var lastLumaUpdateTime = 0L
    private val LUMA_UPDATE_INTERVAL = 200L // Actualizar cada 200ms
    
    // Telemetría ligera para debugging
    private var clipTelemetry = mutableMapOf<String, Any>()
    
    // Callback para notificar cambios
    var onExposureChanged: ((luma: Float, evCompensation: Int, torchEnabled: Boolean) -> Unit)? = null
    
    // Variables para evitar ajustes excesivos
    private var lastAdjustmentTime = 0L
    private val MIN_ADJUSTMENT_INTERVAL = 1000L // 1 segundo entre ajustes automáticos
    private var consecutiveTouchCount = 0
    private var lastTouchTime = 0L
    private val TOUCH_RESET_TIME = 3000L // Reset contador de toques después de 3 segundos
    
    init {
        setupTouchListener()
        // Restaurar última configuración EV del usuario
        currentEvCompensation = sharedPrefs.getInt("last_ev_compensation", 0)
    }
    
    private fun setupTouchListener() {
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
                performTouchExposure(event.x, event.y, view.width, view.height)
                return@setOnTouchListener true
            }
            false
        }
    }
    
    fun setCamera(camera: Camera) {
        this.camera = camera
        this.cameraControl = camera.cameraControl
        this.cameraInfo = camera.cameraInfo
        
        try {
            // Obtener controles Camera2 para funciones avanzadas
            this.camera2CameraControl = Camera2CameraControl.from(cameraControl!!)
            this.camera2CameraInfo = Camera2CameraInfo.from(cameraInfo!!)
            
            // Verificar soporte de funciones
            checkCameraCapabilities()
            
            // Restaurar EV del usuario si está soportado
            if (isExposureCompensationSupported && currentEvCompensation != 0) {
                Log.i("ExposureControl", "Restaurando EV del usuario: $currentEvCompensation")
                adjustEvCompensation(currentEvCompensation)
            }
            
        } catch (e: Exception) {
            Log.w("ExposureControl", "No se pudo acceder a Camera2 interop: ${e.message}")
        }
        
        // Configurar análisis de imagen para medición de luma
        setupImageAnalysis()
    }
    
    private fun checkCameraCapabilities() {
        try {
            camera2CameraInfo?.let { info ->
                val cameraCharacteristics = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                isExposureCompensationSupported = cameraCharacteristics != null
                
                val flashAvailable = info.getCameraCharacteristic(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                isTorchSupported = flashAvailable == true
                
                Log.i("ExposureControl", "Soporte - EV: $isExposureCompensationSupported, Linterna: $isTorchSupported")
            }
        } catch (e: Exception) {
            Log.w("ExposureControl", "Error verificando capacidades: ${e.message}")
            // Asumir soporte básico como fallback
            isExposureCompensationSupported = true
            isTorchSupported = true
        }
    }
    
    private fun setupImageAnalysis() {
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLumaUpdateTime >= LUMA_UPDATE_INTERVAL) {
                    val (luma, clippingPercentage) = calculateLumaWithHistogram(imageProxy)
                    if (luma > 0) {
                        currentLuma = luma
                        lastLumaUpdateTime = currentTime
                        
                        // Actualizar telemetría
                        updateTelemetry(luma, clippingPercentage)
                        
                        // Procesar ajustes automáticos de exposición
                        processExposureAdjustment(luma, clippingPercentage)
                    }
                }
            } catch (e: Exception) {
                Log.w("ExposureControl", "Error calculando luma: ${e.message}")
            } finally {
                imageProxy.close()
            }
        }
    }
    
    private fun calculateLumaWithHistogram(imageProxy: ImageProxy): Pair<Float, Float> {
        try {
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                return Pair(0f, 0f)
            }
            
            val yBuffer: ByteBuffer = imageProxy.planes[0].buffer
            val ySize = yBuffer.remaining()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)
            
            // Calcular en región central (40% del centro)
            val width = imageProxy.width
            val height = imageProxy.height
            val centerX = width / 2
            val centerY = height / 2
            val regionWidth = (width * 0.4).toInt()
            val regionHeight = (height * 0.4).toInt()
            
            val startX = centerX - regionWidth / 2
            val endX = centerX + regionWidth / 2
            val startY = centerY - regionHeight / 2
            val endY = centerY + regionHeight / 2
            
            // Crear histograma de 256 bins para valores Y
            val histogram = IntArray(256) { 0 }
            var pixelCount = 0
            
            for (y in startY until endY) {
                for (x in startX until endX) {
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        val index = y * width + x
                        if (index < yArray.size) {
                            val luma = yArray[index].toInt() and 0xFF
                            histogram[luma]++
                            pixelCount++
                        }
                    }
                }
            }
            
            if (pixelCount == 0) return Pair(0f, 0f)
            
            // Calcular luma promedio ponderado desde histograma
            var weightedSum = 0L
            for (i in histogram.indices) {
                weightedSum += i * histogram[i]
            }
            val averageLuma = weightedSum.toFloat() / pixelCount / 255f
            
            // Calcular porcentaje de clipping (píxeles ≥250 en 8-bit)
            var clippedPixels = 0
            for (i in 250..255) {
                clippedPixels += histogram[i]
            }
            val clippingPercentage = clippedPixels.toFloat() / pixelCount
            
            return Pair(averageLuma, clippingPercentage)
            
        } catch (e: Exception) {
            Log.w("ExposureControl", "Error en cálculo de luma con histograma: ${e.message}")
            return Pair(0f, 0f)
        }
    }
    
    private fun updateTelemetry(luma: Float, clippingPercentage: Float) {
        clipTelemetry["luma_avg"] = String.format("%.3f", luma)
        clipTelemetry["ev_compensation"] = currentEvCompensation
        clipTelemetry["torch_enabled"] = isTorchEnabled
        clipTelemetry["ae_locked"] = isAeLocked
        clipTelemetry["clipping_percent"] = String.format("%.2f", clippingPercentage * 100)
        clipTelemetry["timestamp"] = System.currentTimeMillis()
    }
    
    private fun performTouchExposure(touchX: Float, touchY: Float, viewWidth: Int, viewHeight: Int) {
        cameraControl?.let { control ->
            try {
                val currentTime = System.currentTimeMillis()
                
                // Reset contador de toques si ha pasado suficiente tiempo
                if (currentTime - lastTouchTime > TOUCH_RESET_TIME) {
                    consecutiveTouchCount = 0
                }
                
                consecutiveTouchCount++
                lastTouchTime = currentTime
                
                // Evitar demasiados toques consecutivos (spam)
                if (consecutiveTouchCount > 5) {
                    Log.w("ExposureControl", "Demasiados toques consecutivos, ignorando")
                    return
                }
                
                // Convertir coordenadas de vista a coordenadas normalizadas
                val x = touchX / viewWidth
                val y = touchY / viewHeight
                
                // Crear punto de medición
                val meteringPoint = previewView.meteringPointFactory.createPoint(x, y)
                
                // Configurar medición puntual AE
                val action = androidx.camera.core.FocusMeteringAction.Builder(meteringPoint)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                // Ejecutar medición
                control.startFocusAndMetering(action)
                
                Log.d("ExposureControl", "Medición puntual #$consecutiveTouchCount en coordenadas: ($x, $y)")
                
            } catch (e: Exception) {
                Log.e("ExposureControl", "Error en medición puntual: ${e.message}")
            }
        }
    }
    
    private fun processExposureAdjustment(luma: Float, clippingPercentage: Float) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Si estamos grabando y AE está bloqueado, no hacer ajustes
            if (isRecording && isAeLocked) {
                return
            }
            
            // Si no hay soporte para EV, solo hacer medición puntual
            if (!isExposureCompensationSupported) {
                return
            }
            
            // Evitar ajustes demasiado frecuentes
            if (currentTime - lastAdjustmentTime < MIN_ADJUSTMENT_INTERVAL) {
                return
            }
            
            // Manejar clipping primero
            if (clippingPercentage > CLIPPING_THRESHOLD && currentEvCompensation > MIN_EV_COMPENSATION) {
                Log.i("ExposureControl", "Detectado clipping: ${String.format("%.2f", clippingPercentage * 100)}%, bajando EV")
                lastAdjustmentTime = currentTime
                adjustEvCompensation(currentEvCompensation - 1)
                return
            }
            
            // Aplicar histéresis para evitar bombeo
            val shouldAdjust = when {
                luma < CRITICAL_LOW_LUMA -> true
                luma > CRITICAL_HIGH_LUMA -> true
                luma < HYSTERESIS_MIN || luma > HYSTERESIS_MAX -> {
                    // Solo ajustar si estamos fuera del rango objetivo
                    luma < TARGET_LUMA_MIN || luma > TARGET_LUMA_MAX
                }
                else -> false
            }
            
            if (!shouldAdjust) {
                return
            }
            
            // Determinar ajuste de EV
            val newEvCompensation = when {
                luma < CRITICAL_LOW_LUMA -> min(currentEvCompensation + 1, MAX_EV_COMPENSATION)
                luma > CRITICAL_HIGH_LUMA -> max(currentEvCompensation - 1, MIN_EV_COMPENSATION)
                else -> currentEvCompensation
            }
            
            // Aplicar ajuste de EV si cambió
            if (newEvCompensation != currentEvCompensation) {
                lastAdjustmentTime = currentTime
                adjustEvCompensation(newEvCompensation)
            }
            
            // Control de linterna (solo si está soportada)
            if (isTorchSupported) {
                handleTorchControl(luma)
            }
            
        } catch (e: Exception) {
            Log.e("ExposureControl", "Error procesando ajuste de exposición: ${e.message}")
        }
    }
    
    private fun adjustEvCompensation(newEv: Int) {
        if (!isExposureCompensationSupported) {
            Log.w("ExposureControl", "Compensación EV no soportada en este dispositivo")
            return
        }
        
        val clampedEv = max(MIN_EV_COMPENSATION, min(MAX_EV_COMPENSATION, newEv))
        
        cameraControl?.setExposureCompensationIndex(clampedEv)?.addListener({
            currentEvCompensation = clampedEv
            // Guardar en SharedPreferences para persistir la configuración del usuario
            sharedPrefs.edit().putInt("last_ev_compensation", clampedEv).apply()
            Log.i("ExposureControl", "EV ajustado a: $clampedEv (luma: ${String.format("%.3f", currentLuma)})")
            notifyExposureChanged()
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun handleTorchControl(luma: Float) {
        if (!isTorchSupported) {
            return
        }
        
        val shouldEnableTorch = when {
            !isTorchEnabled && luma < TORCH_ENABLE_THRESHOLD && currentEvCompensation >= MAX_EV_COMPENSATION -> true
            isTorchEnabled && luma > TORCH_DISABLE_THRESHOLD -> false
            else -> return
        }
        
        if (shouldEnableTorch != isTorchEnabled) {
            cameraControl?.enableTorch(shouldEnableTorch)?.addListener({
                isTorchEnabled = shouldEnableTorch
                Log.i("ExposureControl", "Linterna ${if (shouldEnableTorch) "encendida" else "apagada"} (luma: ${String.format("%.3f", luma)})")
                notifyExposureChanged()
            }, ContextCompat.getMainExecutor(context))
        }
    }
    
    fun onRecordingStarted() {
        isRecording = true
        
        // Bloquear AE si la luma está en rango óptimo
        if (currentLuma >= RECORDING_LOCK_MIN && currentLuma <= RECORDING_LOCK_MAX) {
            lockAutoExposure(true)
            Log.i("ExposureControl", "AE bloqueado para grabación (luma: ${String.format("%.3f", currentLuma)})")
        }
    }
    
    fun onRecordingStopped() {
        isRecording = false
        
        // Desbloquear AE
        if (isAeLocked) {
            lockAutoExposure(false)
            Log.i("ExposureControl", "AE desbloqueado después de grabación")
        }
    }
    
    private fun lockAutoExposure(lock: Boolean) {
        try {
            // Usar Camera2 interop para bloqueo AE/AWB sin reiniciar use cases
            camera2CameraControl?.let { control ->
                val captureRequestOptions = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lock)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lock)
                    .build()
                
                control.captureRequestOptions = captureRequestOptions
                isAeLocked = lock
                
                Log.i("ExposureControl", "AE/AWB ${if (lock) "bloqueado" else "desbloqueado"} vía Camera2 interop")
            } ?: run {
                Log.w("ExposureControl", "Camera2 interop no disponible para bloqueo AE/AWB")
            }
                
        } catch (e: Exception) {
            Log.e("ExposureControl", "Error configurando bloqueo AE/AWB: ${e.message}")
        }
    }
    
    private fun notifyExposureChanged() {
        onExposureChanged?.invoke(currentLuma, currentEvCompensation, isTorchEnabled)
    }
    
    fun getImageAnalysis(): ImageAnalysis? = imageAnalysis
    
    fun getCurrentLuma(): Float = currentLuma
    
    fun getCurrentEvCompensation(): Int = currentEvCompensation
    
    fun isTorchEnabled(): Boolean = isTorchEnabled
    
    fun isExposureSupported(): Boolean = isExposureCompensationSupported
    
    fun isTorchSupported(): Boolean = isTorchSupported
    
    fun getClipTelemetry(): Map<String, Any> = clipTelemetry.toMap()
    
    fun logTelemetryForClip(clipName: String) {
        if (clipTelemetry.isNotEmpty()) {
            Log.i("ExposureControl", "Telemetría para clip '$clipName': $clipTelemetry")
        }
    }
    
    fun cleanup() {
        try {
            // Log telemetría final
            if (clipTelemetry.isNotEmpty()) {
                Log.i("ExposureControl", "Telemetría final de sesión: $clipTelemetry")
            }
            
            // Restablecer configuración solo si es necesario
            if (isTorchEnabled && isTorchSupported) {
                cameraControl?.enableTorch(false)
            }
            
            if (isAeLocked) {
                lockAutoExposure(false)
            }
            
            // Solo resetear EV si no es la configuración preferida del usuario
            val userPreferredEv = sharedPrefs.getInt("last_ev_compensation", 0)
            if (currentEvCompensation != userPreferredEv && isExposureCompensationSupported) {
                cameraControl?.setExposureCompensationIndex(userPreferredEv)
            }
            
            // Limpiar referencias
            imageAnalysis = null
            camera = null
            cameraControl = null
            cameraInfo = null
            camera2CameraControl = null
            camera2CameraInfo = null
            clipTelemetry.clear()
            
        } catch (e: Exception) {
            Log.w("ExposureControl", "Error en cleanup: ${e.message}")
        }
    }
}
