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
    
    // Variable para identificar si está usando la cámara frontal
    private var isFrontCamera = true
    
    // SharedPreferences para persistir configuración del usuario
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("exposure_settings", Context.MODE_PRIVATE)
    
    // Verificación de soporte de funciones
    private var isExposureCompensationSupported = false
    private var isTorchSupported = false
    
    // Parámetros de exposición optimizados para MediaPipe y detección de keypoints
    // Rangos más estrictos para mejor detección de manos y poses
    // Configuración para cámara TRASERA (principal)
    private val TARGET_LUMA_MIN_BACK = 0.40f    // Reducido para mejor contraste
    private val TARGET_LUMA_MAX_BACK = 0.50f    // Reducido para evitar sobreexposición
    private val HYSTERESIS_MIN_BACK = 0.38f     // Ajustado para detección más sensible
    private val HYSTERESIS_MAX_BACK = 0.52f     // Ajustado para evitar bombeo
    private val CRITICAL_LOW_LUMA_BACK = 0.30f  // Muy oscuro, keypoints no visibles
    private val CRITICAL_HIGH_LUMA_BACK = 0.55f // Muy brillante, keypoints se pierden

    // Configuración para cámara FRONTAL (selfie)
    private val TARGET_LUMA_MIN_FRONT = 0.45f   // Más brillante para selfies
    private val TARGET_LUMA_MAX_FRONT = 0.55f   // Más tolerante a sobreexposición
    private val HYSTERESIS_MIN_FRONT = 0.43f    // Ajustado para selfies
    private val HYSTERESIS_MAX_FRONT = 0.57f    // Más amplio para selfies
    private val CRITICAL_LOW_LUMA_FRONT = 0.35f // Más tolerante para selfies
    private val CRITICAL_HIGH_LUMA_FRONT = 0.60f // Más tolerante para selfies

    // Parámetros comunes
    private val TORCH_ENABLE_THRESHOLD = 0.25f // Activar linterna antes
    private val TORCH_DISABLE_THRESHOLD = 0.35f // Mantener equilibrio
    private val RECORDING_LOCK_MIN = 0.38f // Rango de bloqueo durante grabación
    private val RECORDING_LOCK_MAX = 0.52f
    private val CLIPPING_THRESHOLD = 0.010f // 1% - Más estricto para MediaPipe

    // Propiedades calculadas según la cámara actual
    private val TARGET_LUMA_MIN: Float get() = if (isFrontCamera) TARGET_LUMA_MIN_FRONT else TARGET_LUMA_MIN_BACK
    private val TARGET_LUMA_MAX: Float get() = if (isFrontCamera) TARGET_LUMA_MAX_FRONT else TARGET_LUMA_MAX_BACK
    private val HYSTERESIS_MIN: Float get() = if (isFrontCamera) HYSTERESIS_MIN_FRONT else HYSTERESIS_MIN_BACK
    private val HYSTERESIS_MAX: Float get() = if (isFrontCamera) HYSTERESIS_MAX_FRONT else HYSTERESIS_MAX_BACK
    private val CRITICAL_LOW_LUMA: Float get() = if (isFrontCamera) CRITICAL_LOW_LUMA_FRONT else CRITICAL_LOW_LUMA_BACK
    private val CRITICAL_HIGH_LUMA: Float get() = if (isFrontCamera) CRITICAL_HIGH_LUMA_FRONT else CRITICAL_HIGH_LUMA_BACK
    
    // Rango de compensación EV (se actualizará con los límites del dispositivo)
    private var MIN_EV_COMPENSATION = -2
    private var MAX_EV_COMPENSATION = 2
    
    // Estado actual
    private var currentEvCompensation = 0
    private var isTorchEnabled = false
    private var isAeLocked = false
    private var isRecording = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // EMA para suavizar luma (α≈0.4)
    private var smoothedLuma = 0.5f
    private val EMA_ALPHA = 0.4f
    
    // AE Lock automático
    private var stableTimestamp = 0L
    private val STABLE_DURATION_MS = 1200L // 1.2 segundos
    private var lastStableLuma = 0f
    
    // Análisis de imagen para medición de luma
    private var imageAnalysis: ImageAnalysis? = null
    private var currentLuma = 0.5f
    private var lastLumaUpdateTime = 0L
    private val LUMA_UPDATE_INTERVAL = 150L // Más frecuente para MediaPipe
    
    // Telemetría ligera para debugging
    private var clipTelemetry = mutableMapOf<String, Any>()
    
    // Callback para notificar cambios
    var onExposureChanged: ((luma: Float, evCompensation: Int, torchEnabled: Boolean) -> Unit)? = null

    // Callback para feedback inmediato al tocar la pantalla
    var onTouchFeedback: ((changeAmount: Int, newEv: Int, cameraType: String) -> Unit)? = null
    
    // Variables para evitar ajustes excesivos (optimizado para MediaPipe)
    private var lastAdjustmentTime = 0L
    private val MIN_ADJUSTMENT_INTERVAL = 600L // Más rápido para mejor detección
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
    
    fun setFrontCamera(isFront: Boolean) {
        val wasChanged = isFrontCamera != isFront
        isFrontCamera = isFront
        Log.d("ExposureControl", "Configurado para cámara: ${if (isFront) "FRONTAL" else "TRASERA"} - Medición manual disponible")
        
        if (wasChanged) {
            Log.d("ExposureControl", "🔄 Cambio de cámara detectado - listo para medición manual al tocar pantalla")
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
        
        Log.d("ExposureControl", "Cámara configurada - medición manual lista para toques de pantalla")
    }
    
    private fun checkCameraCapabilities() {
        try {
            camera2CameraInfo?.let { info ->
                // Obtener rango real de exposición del dispositivo
                val exposureRange = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (exposureRange != null) {
                    MIN_EV_COMPENSATION = exposureRange.lower
                    MAX_EV_COMPENSATION = exposureRange.upper
                    isExposureCompensationSupported = true
                    Log.i("ExposureControl", "Rango EV del dispositivo: $MIN_EV_COMPENSATION a $MAX_EV_COMPENSATION")
                } else {
                    isExposureCompensationSupported = false
                }
                
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
                    val (rawLuma, clippingPercentage) = calculateLumaWithHistogram(imageProxy)
                    if (rawLuma > 0) {
                        // Aplicar EMA para suavizar luma
                        smoothedLuma = if (smoothedLuma == 0.5f) {
                            rawLuma // Primera medición
                        } else {
                            EMA_ALPHA * rawLuma + (1 - EMA_ALPHA) * smoothedLuma
                        }
                        
                        currentLuma = smoothedLuma
                        lastLumaUpdateTime = currentTime
                        
                        // Actualizar telemetría
                        updateTelemetry(smoothedLuma, clippingPercentage)
                        
                        // Procesar ajustes automáticos de exposición con luma suavizada
                        processExposureAdjustment(smoothedLuma, clippingPercentage)
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
                // Mostrar feedback incluso en caso de spam
                onTouchFeedback?.invoke(0, currentEvCompensation, if (isFrontCamera) "FRONTAL" else "TRASERA")
                return
            }

            // Validar coordenadas
            if (viewWidth <= 0 || viewHeight <= 0) {
                Log.w("ExposureControl", "Dimensiones de vista inválidas: ${viewWidth}x${viewHeight}")
                onTouchFeedback?.invoke(0, currentEvCompensation, if (isFrontCamera) "FRONTAL" else "TRASERA")
                return
            }

            if (touchX < 0 || touchX > viewWidth || touchY < 0 || touchY > viewHeight) {
                Log.w("ExposureControl", "Coordenadas de toque fuera de límites: ($touchX, $touchY)")
                onTouchFeedback?.invoke(0, currentEvCompensation, if (isFrontCamera) "FRONTAL" else "TRASERA")
                return
            }

            // Guardar EV anterior para mostrar el cambio
            val previousEvCompensation = currentEvCompensation

            // Convertir coordenadas de vista a coordenadas normalizadas (0.0 - 1.0)
            val normalizedX = touchX / viewWidth
            val normalizedY = touchY / viewHeight

            // Mostrar feedback inmediato del toque (antes de cualquier procesamiento)
            val cameraType = if (isFrontCamera) "FRONTAL" else "TRASERA"
            onTouchFeedback?.invoke(0, currentEvCompensation, cameraType) // 0 = procesando

            cameraControl?.let { control ->
                // Crear punto de medición para el toque
                val meteringPoint = previewView.meteringPointFactory.createPoint(normalizedX, normalizedY)

                // Configurar medición puntual de enfoque y exposición
                val action = androidx.camera.core.FocusMeteringAction.Builder(meteringPoint)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                // Ejecutar medición
                val meteringResult = control.startFocusAndMetering(action)

                // Añadir callback para detectar cambios y ajustar exposición si es necesario
                meteringResult.addListener({
                    try {
                        // Pequeño delay para que la cámara procese el ajuste inicial
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Medir luma en el punto tocado para determinar si necesita ajuste de brillo
                            measureAndAdjustExposure(normalizedX, normalizedY, previousEvCompensation)
                        }, 300)
                    } catch (e: Exception) {
                        Log.e("ExposureControl", "Error procesando resultado de medición: ${e.message}")
                        // Mostrar feedback de error
                        onTouchFeedback?.invoke(0, currentEvCompensation, cameraType)
                    }
                }, ContextCompat.getMainExecutor(context))

                Log.d("ExposureControl", "Medición puntual iniciada en coordenadas: (${normalizedX.format(3)}, ${normalizedY.format(3)}) - $cameraType")

            } ?: run {
                Log.w("ExposureControl", "CameraControl no disponible para medición puntual")
                // Intentar ajuste basado en luma actual sin medición puntual
                measureAndAdjustExposure(normalizedX, normalizedY, previousEvCompensation)
            }

        } catch (e: Exception) {
            Log.e("ExposureControl", "Error en medición puntual: ${e.message}")
            // Mostrar feedback de error
            onTouchFeedback?.invoke(0, currentEvCompensation, if (isFrontCamera) "FRONTAL" else "TRASERA")
        }
    }
    
    // Helper para formatear flotantes
    private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
    
    private fun measureAndAdjustExposure(touchX: Float, touchY: Float, previousEvCompensation: Int) {
        try {
            if (!isExposureCompensationSupported) {
                Log.i("ExposureControl", "Toque registrado - ajuste EV no soportado en este dispositivo")
                // Mostrar feedback visual incluso si no hay soporte EV
                onTouchFeedback?.invoke(0, currentEvCompensation, if (isFrontCamera) "FRONTAL" else "TRASERA")
                return
            }

            // Analizar la luma actual para determinar si necesita ajuste
            val currentLumaValue = currentLuma
            Log.d("ExposureControl", "Medición en toque: luma=${String.format("%.3f", currentLumaValue)}, EV actual=$currentEvCompensation")

            // Usar parámetros específicos de la cámara actual
            val targetLuma = (TARGET_LUMA_MIN + TARGET_LUMA_MAX) / 2f // Punto medio del rango objetivo
            val tolerance = 0.08f // Tolerancia para evitar ajustes innecesarios

            var newEvCompensation = currentEvCompensation
            var changeAmount = 0

            when {
                // Muy oscuro - subir brillo significativamente
                currentLumaValue < (targetLuma - tolerance - 0.1f) -> {
                    newEvCompensation = kotlin.math.min(currentEvCompensation + 2, MAX_EV_COMPENSATION)
                    changeAmount = newEvCompensation - previousEvCompensation
                    Log.i("ExposureControl", "Zona muy oscura detectada - subiendo brillo +2 (${if (isFrontCamera) "frontal" else "trasera"})")
                }
                // Oscuro - subir brillo gradualmente
                currentLumaValue < (targetLuma - tolerance) -> {
                    newEvCompensation = kotlin.math.min(currentEvCompensation + 1, MAX_EV_COMPENSATION)
                    changeAmount = newEvCompensation - previousEvCompensation
                    Log.i("ExposureControl", "Zona oscura detectada - subiendo brillo +1 (${if (isFrontCamera) "frontal" else "trasera"})")
                }
                // Muy brillante - bajar brillo significativamente
                currentLumaValue > (targetLuma + tolerance + 0.1f) -> {
                    newEvCompensation = kotlin.math.max(currentEvCompensation - 2, MIN_EV_COMPENSATION)
                    changeAmount = newEvCompensation - previousEvCompensation
                    Log.i("ExposureControl", "Zona muy brillante detectada - bajando brillo -2 (${if (isFrontCamera) "frontal" else "trasera"})")
                }
                // Brillante - bajar brillo gradualmente
                currentLumaValue > (targetLuma + tolerance) -> {
                    newEvCompensation = kotlin.math.max(currentEvCompensation - 1, MIN_EV_COMPENSATION)
                    changeAmount = newEvCompensation - previousEvCompensation
                    Log.i("ExposureControl", "Zona brillante detectada - bajando brillo -1 (${if (isFrontCamera) "frontal" else "trasera"})")
                }
                // En rango óptimo
                else -> {
                    Log.i("ExposureControl", "Luminosidad óptima - sin ajuste necesario (${if (isFrontCamera) "frontal" else "trasera"})")
                    changeAmount = 0
                }
            }

            // Mostrar feedback inmediato del toque
            val cameraType = if (isFrontCamera) "FRONTAL" else "TRASERA"
            onTouchFeedback?.invoke(changeAmount, newEvCompensation, cameraType)

            // Aplicar ajuste si es diferente
            if (newEvCompensation != currentEvCompensation) {
                val evChange = newEvCompensation - previousEvCompensation
                val changeText = if (evChange > 0) "+$evChange" else "$evChange"

                Log.i("ExposureControl", "Ajuste manual por toque: EV $previousEvCompensation → $newEvCompensation ($changeText)")
                Log.i("ExposureControl", "Coordenadas: (${touchX.format(2)}, ${touchY.format(2)}) | Luma: ${String.format("%.3f", currentLumaValue)} | Cámara: $cameraType")

                adjustEvCompensation(newEvCompensation)
            } else {
                Log.i("ExposureControl", "Toque manual #$consecutiveTouchCount: Sin cambio de EV necesario (${touchX.format(2)}, ${touchY.format(2)}) | Cámara: $cameraType")
            }

        } catch (e: Exception) {
            Log.e("ExposureControl", "Error ajustando exposición por toque: ${e.message}")
            // Mostrar feedback de error
            onTouchFeedback?.invoke(0, currentEvCompensation, if (isFrontCamera) "FRONTAL" else "TRASERA")
        }
    }
    
    private fun processExposureAdjustment(luma: Float, clippingPercentage: Float) {
        try {
            // Solo actualizar mediciones - NO hacer ajustes automáticos
            val currentTime = System.currentTimeMillis()
            
            // Actualizar luma actual para referencia
            currentLuma = luma
            
            // Chequear AE Lock automático (mantener funcionalidad de bloqueo durante grabación)
            checkAutoAeLock(luma, currentTime)
            
            // Actualizar telemetría sin ajustes automáticos
            val inTargetRange = luma >= TARGET_LUMA_MIN && luma <= TARGET_LUMA_MAX
            
            Log.v("ExposureControl", "📊 Medición: luma=${String.format("%.3f", luma)}, EV=$currentEvCompensation, en_rango=$inTargetRange")
            
            // Notificar cambios solo si hay diferencias significativas
            notifyExposureChanged()
            
        } catch (e: Exception) {
            Log.e("ExposureControl", "Error procesando medición de exposición: ${e.message}")
        }
    }
    
    private fun checkAutoAeLock(luma: Float, currentTime: Long) {
        val inStableRange = luma >= TARGET_LUMA_MIN && luma <= TARGET_LUMA_MAX
        val outsideUnlockRange = luma > 0.55f || luma < 0.35f
        
        if (inStableRange) {
            if (stableTimestamp == 0L) {
                stableTimestamp = currentTime
                lastStableLuma = luma
            } else if (currentTime - stableTimestamp >= STABLE_DURATION_MS && !isAeLocked) {
                lockAutoExposure(true)
                Log.i("ExposureControl", "AE bloqueado automáticamente tras ${STABLE_DURATION_MS}ms estable (luma: ${String.format("%.3f", luma)})")
            }
        } else if (outsideUnlockRange && isAeLocked) {
            lockAutoExposure(false)
            resetStableTimer()
            Log.i("ExposureControl", "AE desbloqueado automáticamente (luma fuera de rango: ${String.format("%.3f", luma)})")
        } else if (!inStableRange) {
            resetStableTimer()
        }
    }
    
    private fun resetStableTimer() {
        stableTimestamp = 0L
        lastStableLuma = 0f
    }
    
    private fun adjustEvCompensation(newEv: Int) {
        if (!isExposureCompensationSupported) {
            Log.w("ExposureControl", "Compensación EV no soportada en este dispositivo")
            return
        }
        
        val clampedEv = kotlin.math.max(MIN_EV_COMPENSATION, kotlin.math.min(MAX_EV_COMPENSATION, newEv))
        
        cameraControl?.setExposureCompensationIndex(clampedEv)?.addListener({
            currentEvCompensation = clampedEv
            // Guardar en SharedPreferences para persistir la configuración del usuario
            sharedPrefs.edit().putInt("last_ev_compensation", clampedEv).apply()
            
            // Log específico para MediaPipe optimización
            val mediaPipeStatus = when {
                currentLuma < CRITICAL_LOW_LUMA -> "CRITICO-OSCURO (keypoints no detectables)"
                currentLuma > CRITICAL_HIGH_LUMA -> "CRITICO-BRILLANTE (keypoints saturados)"
                currentLuma < TARGET_LUMA_MIN -> "SUB-OPTIMO (mejorando contraste)"
                currentLuma > TARGET_LUMA_MAX -> "SOBRE-OPTIMO (reduciendo brillo)"
                else -> "OPTIMO (ideal para MediaPipe)"
            }
            
            Log.i("ExposureControl", "EV ajustado a: $clampedEv (luma: ${String.format("%.3f", currentLuma)}) - MediaPipe: $mediaPipeStatus")
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
