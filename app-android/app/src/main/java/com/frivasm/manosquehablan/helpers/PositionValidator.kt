package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import kotlin.math.*

/**
 * Validador de Posición previo a la grabación
 * Lee TYPE_ROTATION_VECTOR a ≥10 Hz, remapea con SensorManager.remapCoordinateSystem según Display.getRotation(),
 * calcula desviación vertical, aplica doble EMA y usa histeresis + debounce
 */
class PositionValidator(
    private val context: Context,
    private val onPositionChanged: (PositionState, Float, Float, Boolean) -> Unit, // Agregado hasGyroscope
    private val onRecordingAllowed: (Boolean) -> Unit = {}
) : SensorEventListener {

    companion object {
        private const val TAG = "PositionValidator"
        private const val TARGET_FREQUENCY_MS = 80L // Más frecuente para mayor responsividad (12.5 Hz)
        private const val FAST_EMA_ALPHA = 0.7f // Para salir a rojo (más reactivo)
        private const val SLOW_EMA_ALPHA = 0.25f // Para entrar a verde (ligeramente más reactivo)
        private const val GREEN_THRESHOLD = 20.0f // ≤20° para GREEN (70-90° rango)
        private const val RED_THRESHOLD = 21.0f // >21° para RED
        private const val CRITICAL_THRESHOLD = 25.0f // ≥25° para CRITICAL
        private const val GREEN_STABLE_TIME = 1200L // 1.2s estable para GREEN (reducido para mayor responsividad)
        private const val RED_TIME = 600L // 0.6s para pasar a RED (reducido para mayor responsividad)
        
        // Nuevos parámetros para suavidad
        private const val SMOOTH_TRANSITION_THRESHOLD = 2.0f // Grados para transición suave
        private const val UI_UPDATE_INTERVAL = 150L // Reducir frecuencia de updates UI para mayor responsividad
        
        /**
         * Calcula la desviación de un valor respecto a un rango permitido
         * @param value Valor actual del ángulo
         * @param minRange Límite inferior del rango (70°)
         * @param maxRange Límite superior del rango (90°)
         * @return Desviación en grados (0 si está dentro del rango)
         */
        private fun calculateDeviationFromRange(value: Float, minRange: Float, maxRange: Float): Float {
            return when {
                value < minRange -> minRange - value  // Debajo del rango
                value > maxRange -> value - maxRange  // Arriba del rango
                else -> 0f  // Dentro del rango
            }
        }
    }

    enum class PositionState {
        GREEN,    // Posición correcta (desviación ≤ 20° del rango 70-90°)
        RED,      // Posición incorrecta (desviación > 21° del rango 70-90°)
        CRITICAL  // Posición crítica (desviación ≥ 25° del rango 70-90°)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentState = PositionState.RED
    private var lastSensorTime = 0L

    // Variables para EMA doble
    private var fastEMA = 0.0f
    private var slowEMA = 0.0f
    private var isEMAInitialized = false

    // Variables para debounce/histeresis
    private var greenStartTime = 0L
    private var redStartTime = 0L
    private var isInGreenThreshold = false
    private var isInRedThreshold = false

    private var isActive = false
    private var isRecordingAllowed = false
    private var lastUIUpdateTime = 0L
    private var lastNotifiedAngle = 0f

    val hasGyroscope: Boolean
        get() = rotationVectorSensor != null

    val canRecord: Boolean
        get() = isRecordingAllowed || !hasGyroscope // Permitir grabación si no hay giroscopio

    /**
     * Método para notificar que la verificación de ángulo ha sido completada por la UI
     * Solo debe ser llamado desde SmoothPositionModal
     */
    fun completeAngleVerification() {
        if (isVerifyingAngle && currentState == PositionState.GREEN) {
            isVerifyingAngle = false
            updateRecordingAllowed(true)
            Log.i(TAG, "Verificación de ángulo completada por UI - ¡GRABACIÓN PERMITIDA!")
        }
    }

    fun startValidation() {
        if (!hasGyroscope) {
            // Modo manual - sin giroscopio, permitir grabación
            isRecordingAllowed = true
            Log.i(TAG, "Sin giroscopio detectado - Modo manual activado, grabación permitida")
            handler.post {
                onPositionChanged(PositionState.RED, 0f, 90f, false)
                onRecordingAllowed(true)
            }
            return
        }

        if (isActive) return
        isActive = true

        Log.i(TAG, "Iniciando validación de posición con giroscopio")

        // Reset de variables
        isEMAInitialized = false
        fastEMA = 0.0f
        slowEMA = 0.0f
        greenStartTime = 0L
        redStartTime = 0L
        isInGreenThreshold = false
        isInRedThreshold = false
        currentState = PositionState.RED
        isRecordingAllowed = false

        // Registrar listener del sensor a alta frecuencia
        sensorManager.registerListener(
            this,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stopValidation() {
        if (!isActive) return
        isActive = false
        isRecordingAllowed = false
        Log.i(TAG, "Deteniendo validación de posición")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isActive || event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return

        val currentTime = System.currentTimeMillis()

        // Throttle a ≥10 Hz (máximo cada 100ms)
        if (currentTime - lastSensorTime < TARGET_FREQUENCY_MS) return
        lastSensorTime = currentTime

        // Obtener matrix de rotación del rotation vector
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remapear según orientación de la pantalla
        val remappedMatrix = remapCoordinateSystem(rotationMatrix)

        // Calcular ángulos de orientación
        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        // Convertir a grados
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Calcular desviación del rango 70-90°: qué tan fuera del rango está el dispositivo
        val pitchDev = calculateDeviationFromRange(abs(pitch), 70f, 90f)
        val rollDev = calculateDeviationFromRange(abs(roll), 70f, 90f)
        val deviation = min(pitchDev, rollDev)

        // Log detallado de ángulos cada 500ms para no saturar
        if (currentTime % 500 < TARGET_FREQUENCY_MS) {
            Log.d(TAG, "Ángulos - Azimuth: ${String.format("%.1f", azimuth)}°, " +
                    "Pitch: ${String.format("%.1f", pitch)}°, " +
                    "Roll: ${String.format("%.1f", roll)}°")
            Log.d(TAG, "Desviaciones - Pitch: ${String.format("%.1f", pitchDev)}°, " +
                    "Roll: ${String.format("%.1f", rollDev)}°, " +
                    "Final: ${String.format("%.1f", deviation)}° (Rango aceptable: 70-90°, desv≤20° para GREEN)")
        }

        // Aplicar doble EMA
        applyDoubleEMA(deviation)

        // Procesar estado con histeresis y debounce
        processStateWithHysteresis(currentTime)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesario para TYPE_ROTATION_VECTOR
    }

    private fun remapCoordinateSystem(originalMatrix: FloatArray): FloatArray {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val remappedMatrix = FloatArray(9)

        when (display?.rotation) {
            Surface.ROTATION_0 -> {
                // Retrato normal
                System.arraycopy(originalMatrix, 0, remappedMatrix, 0, 9)
            }
            Surface.ROTATION_90 -> {
                // Paisaje (rotado 90° a la izquierda)
                SensorManager.remapCoordinateSystem(
                    originalMatrix, 
                    SensorManager.AXIS_Y, 
                    SensorManager.AXIS_MINUS_X, 
                    remappedMatrix
                )
            }
            Surface.ROTATION_180 -> {
                // Retrato invertido
                SensorManager.remapCoordinateSystem(
                    originalMatrix, 
                    SensorManager.AXIS_MINUS_X, 
                    SensorManager.AXIS_MINUS_Y, 
                    remappedMatrix
                )
            }
            Surface.ROTATION_270 -> {
                // Paisaje (rotado 90° a la derecha)
                SensorManager.remapCoordinateSystem(
                    originalMatrix, 
                    SensorManager.AXIS_MINUS_Y, 
                    SensorManager.AXIS_X, 
                    remappedMatrix
                )
            }
            else -> {
                // Caso por defecto - retrato normal
                System.arraycopy(originalMatrix, 0, remappedMatrix, 0, 9)
            }
        }

        return remappedMatrix
    }

    private fun applyDoubleEMA(deviation: Float) {
        if (!isEMAInitialized) {
            fastEMA = deviation
            slowEMA = deviation
            isEMAInitialized = true
        } else {
            // EMA rápido para salir a rojo
            fastEMA = FAST_EMA_ALPHA * deviation + (1 - FAST_EMA_ALPHA) * fastEMA
            // EMA lento para entrar a verde
            slowEMA = SLOW_EMA_ALPHA * deviation + (1 - SLOW_EMA_ALPHA) * slowEMA
        }
    }

    private fun processStateWithHysteresis(currentTime: Long) {
        val currentDeviation = when (currentState) {
            PositionState.GREEN -> slowEMA  // Usar EMA lento para mantener verde
            else -> fastEMA  // Usar EMA rápido para cambios a rojo/crítico
        }

        // Log del estado actual cada 1 segundo
        if (currentTime % 1000 < TARGET_FREQUENCY_MS) {
            Log.d(TAG, "Estado actual: $currentState, " +
                    "EMA rápido: ${String.format("%.1f", fastEMA)}°, " +
                    "EMA lento: ${String.format("%.1f", slowEMA)}°, " +
                    "Grabación permitida: $isRecordingAllowed")
        }

        // Detección inmediata de CRITICAL
        if (fastEMA >= CRITICAL_THRESHOLD) {
            if (currentState != PositionState.CRITICAL) {
                currentState = PositionState.CRITICAL
                updateRecordingAllowed(false)
                Log.w(TAG, "¡POSICIÓN CRÍTICA! Desviación: ${String.format("%.1f", fastEMA)}°")
                notifyPositionChange(currentTime)
            }
            return
        }

        // Lógica de histeresis para GREEN/RED
        when (currentState) {
            PositionState.GREEN -> {
                // Estamos en GREEN, verificar si salir
                if (fastEMA > RED_THRESHOLD) {
                    if (!isInRedThreshold) {
                        isInRedThreshold = true
                        redStartTime = currentTime
                        Log.d(TAG, "Iniciando transición a RED, desviación: ${String.format("%.1f", fastEMA)}°")
                    } else if (currentTime - redStartTime >= RED_TIME) {
                        currentState = PositionState.RED
                        isInRedThreshold = false
                        isInGreenThreshold = false
                        updateRecordingAllowed(false)
                        Log.w(TAG, "Transición a RED completada tras ${RED_TIME}ms")
                        notifyPositionChange(currentTime)
                    }
                } else {
                    isInRedThreshold = false
                }
            }
            
            PositionState.RED, PositionState.CRITICAL -> {
                // Estamos en RED/CRITICAL, verificar si entrar a GREEN
                if (slowEMA <= GREEN_THRESHOLD) {
                    if (!isInGreenThreshold) {
                        isInGreenThreshold = true
                        greenStartTime = currentTime
                        Log.d(TAG, "Iniciando transición a GREEN, desviación: ${String.format("%.1f", slowEMA)}°")
                    } else if (currentTime - greenStartTime >= GREEN_STABLE_TIME) {
                        currentState = PositionState.GREEN
                        isInGreenThreshold = false
                        isInRedThreshold = false
                        
                        // Para dispositivos con giroscopio, marcar como verificando y no habilitar grabación aún
                        if (hasGyroscope) {
                            isVerifyingAngle = true
                            Log.i(TAG, "Transición a GREEN completada - Iniciando verificación de ángulo (grabación diferida)")
                        } else {
                            // Sin giroscopio, habilitar inmediatamente
                            updateRecordingAllowed(true)
                            Log.i(TAG, "Transición a GREEN completada - ¡GRABACIÓN PERMITIDA! (sin giroscopio)")
                        }
                        
                        notifyPositionChange(currentTime)
                    } else {
                        // Log de progreso hacia GREEN
                        val remainingTime = GREEN_STABLE_TIME - (currentTime - greenStartTime)
                        if (currentTime % 500 < TARGET_FREQUENCY_MS) {
                            Log.d(TAG, "Progreso hacia GREEN: ${String.format("%.1f", remainingTime / 1000f)}s restantes")
                        }
                    }
                } else {
                    isInGreenThreshold = false
                }

                // Si no es CRITICAL, verificar si es RED
                if (currentState == PositionState.CRITICAL && fastEMA < CRITICAL_THRESHOLD) {
                    currentState = PositionState.RED
                    Log.i(TAG, "Saliendo de CRITICAL a RED")
                    notifyPositionChange(currentTime)
                }
            }
        }
    }

    // Bandera para saber si estamos en proceso de verificación de ángulo
    var isVerifyingAngle = false
    
    // Propiedad para acceder al estado de grabación permitida desde fuera
    val isRecordingAllowedState: Boolean
        get() = isRecordingAllowed
    
    private fun updateRecordingAllowed(allowed: Boolean) {
        if (isRecordingAllowed != allowed) {
            isRecordingAllowed = allowed
            Log.i(TAG, "Estado de grabación cambiado: ${if (allowed) "PERMITIDA" else "BLOQUEADA"}")
            
            // Si estamos habilitando la grabación y estamos verificando el ángulo,
            // no notificamos el cambio inmediatamente (se notificará cuando termine la verificación)
            if (!allowed || !isVerifyingAngle) {
                handler.post {
                    onRecordingAllowed(allowed)
                }
            } else {
                Log.i(TAG, "Notificación de grabación permitida retrasada - esperando verificación completa")
            }
        }
    }

    private fun notifyPositionChange(currentTime: Long) {
        // Throttle de UI mejorado para suavidad
        if (currentTime - lastUIUpdateTime < UI_UPDATE_INTERVAL) return
        
        val deviation = when (currentState) {
            PositionState.GREEN -> slowEMA
            else -> fastEMA
        }

        val uxAngle = 90f - deviation  // Ángulo amigable para el usuario

        // Solo notificar si hay cambio significativo de estado o ángulo para suavidad
        val shouldNotify = lastUIUpdateTime == 0L || 
                          kotlin.math.abs(uxAngle - lastNotifiedAngle) > SMOOTH_TRANSITION_THRESHOLD ||
                          currentTime - lastUIUpdateTime > (UI_UPDATE_INTERVAL * 3) // Forzar update cada 600ms

        if (shouldNotify) {
            lastUIUpdateTime = currentTime
            lastNotifiedAngle = uxAngle

            handler.post {
                onPositionChanged(currentState, deviation, uxAngle, hasGyroscope)
            }
        }
    }
}
