package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import com.frivasm.manosquehablan.helpers.*
import com.frivasm.manosquehablan.ui.PositionValidationBanner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GrabarVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGrabarVideoBinding
    private lateinit var cameraExecutor: ExecutorService
    
    // Helpers
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var videoRecordingHelper: VideoRecordingHelper
    private lateinit var videoPauseHelper: VideoPauseHelper
    private lateinit var videoTimerHelper: VideoTimerHelper
    private lateinit var videoFileHelper: VideoFileHelper
    
    // Validador de posición
    private lateinit var positionValidator: PositionValidator
    private lateinit var positionBanner: PositionValidationBanner
    private var isRecordingAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrabarVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar helpers
        initializeHelpers()
        
        // Inicializar validador de posición
        initializePositionValidator()
        
        // Configurar listeners
        setupListeners()
        
        // Verificar permisos
        permissionHelper.verificarPermisos()
        
        if (permissionHelper.allPermissionsGranted()) {
            videoRecordingHelper.iniciarCamara()
        }
        
        // Inicializar estado del botón de pausar (deshabilitado al inicio)
        binding.btnPausar.isEnabled = false
        binding.btnPausar.alpha = 0.5f
        
        // Inicializar estilo del contador (rojo)
        binding.temporizador.background = getDrawable(R.drawable.contador_background)
        binding.temporizador.setTextColor(getColor(android.R.color.white))
    }
    
    private fun initializeHelpers() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        permissionHelper = PermissionHelper(this)
        videoRecordingHelper = VideoRecordingHelper(this, binding, this, cameraExecutor)
        videoTimerHelper = VideoTimerHelper(binding.temporizador)
        videoPauseHelper = VideoPauseHelper(this, binding, videoTimerHelper)
        videoFileHelper = VideoFileHelper(this)
        
        // Configurar callbacks del helper de grabación
        videoRecordingHelper.onRecordingStarted = {
            onRecordingStarted()
        }
        
        videoRecordingHelper.onRecordingStopped = {
            onRecordingStopped()
        }
        
        videoRecordingHelper.onRecordingError = { error ->
            // Solo log para errores de grabación
            Log.e("GrabarVideo", "Error de grabación: $error")
        }
    }
    
    private fun initializePositionValidator() {
        // Inicializar banner de validación de posición
        positionBanner = PositionValidationBanner(this, binding.guiaManos)
        
        // Inicializar validador de posición
        positionValidator = PositionValidator(
            context = this,
            onPositionChanged = { state, deviation, uxAngle, hasGyroscope ->
                // Solo actualizar el banner si no hay giroscopio (modo manual)
                if (!hasGyroscope) {
                    positionBanner.updatePosition(state, deviation, uxAngle, hasGyroscope)
                }
                // Actualizar texto de indicaciones según el estado
                updateIndicationsText(state, uxAngle, hasGyroscope)
            },
            onRecordingAllowed = { allowed ->
                isRecordingAllowed = allowed
                updateRecordButtonState()
            }
        )
        
        // Iniciar validación de posición
        positionValidator.startValidation()
        
        // Estado inicial: bloquear grabación hasta que esté en posición correcta
        if (positionValidator.hasGyroscope) {
            isRecordingAllowed = false
            updateRecordButtonState()
        } else {
            isRecordingAllowed = true
            updateRecordButtonState()
        }
    }
    
    private fun updateIndicationsText(state: PositionValidator.PositionState, uxAngle: Float, hasGyroscope: Boolean) {
        runOnUiThread {
            if (videoRecordingHelper.isRecording()) {
                // Durante grabación, mantener mensaje de grabación
                return@runOnUiThread
            }
            
            when (state) {
                PositionValidator.PositionState.GREEN -> {
                    binding.textIndicaciones.text = "Coloca tus manos dentro del marco"
                }
                PositionValidator.PositionState.RED -> {
                    if (hasGyroscope) {
                        binding.textIndicaciones.text = "Endereza el teléfono (ideal 79-90°) - Actual: ${String.format("%.0f", uxAngle)}°"
                    } else {
                        binding.textIndicaciones.text = "Sin giroscopio: coloca el teléfono verticalmente"
                    }
                }
                PositionValidator.PositionState.CRITICAL -> {
                    if (hasGyroscope) {
                        binding.textIndicaciones.text = "¡Posición crítica! Pon el teléfono en vertical"
                    } else {
                        binding.textIndicaciones.text = "Sin giroscopio: coloca el teléfono verticalmente"
                    }
                }
            }
        }
    }
    
    private fun updateRecordButtonState() {
        runOnUiThread {
            if (videoRecordingHelper.isRecording()) {
                // Si ya está grabando, no cambiar el estado del botón
                return@runOnUiThread
            }
            
            if (isRecordingAllowed) {
                // Habilitar botón de grabar
                binding.btnGrabar.isEnabled = true
                binding.btnGrabar.alpha = 1.0f
                Log.d("GrabarVideo", "Botón de grabar HABILITADO - Posición correcta")
            } else {
                // Deshabilitar botón de grabar
                binding.btnGrabar.isEnabled = false
                binding.btnGrabar.alpha = 0.5f
                Log.d("GrabarVideo", "Botón de grabar DESHABILITADO - Posición incorrecta")
            }
        }
    }
    
    private fun setupListeners() {
        binding.btnGrabar.setOnClickListener {
            // Verificar posición antes de permitir grabar
            if (!isRecordingAllowed && !videoRecordingHelper.isRecording()) {
                Log.w("GrabarVideo", "Intento de grabación bloqueado - Posición incorrecta")
                // El banner ya muestra el mensaje, no necesitamos Toast
                return@setOnClickListener
            }
            
            if (videoRecordingHelper.isRecording()) {
                detenerGrabacion()
            } else {
                iniciarGrabacion()
            }
        }

        binding.btnPausar.setOnClickListener {
            if (videoRecordingHelper.isRecording()) {
                if (videoPauseHelper.isPaused()) {
                    reanudarGrabacion()
                } else {
                    pausarGrabacion()
                }
            }
        }

        binding.btnRotarCamara.setOnClickListener {
            if (videoRecordingHelper.isRecording()) {
                return@setOnClickListener
            }
            videoRecordingHelper.rotarCamara()
        }
        
        binding.btnCerrar.setOnClickListener {
            try {
                if (videoRecordingHelper.isRecording()) {
                    detenerGrabacion()
                }
                finish()
            } catch (e: Exception) {
                Log.e("Camara", "Error al cerrar: ${e.message}")
                finish()
            }
        }
    }
    
    private fun iniciarGrabacion() {
        videoRecordingHelper.iniciarGrabacion()
    }
    
    private fun detenerGrabacion() {
        try {
            videoRecordingHelper.detenerGrabacion()
            // onRecordingStopped() ya se llama desde el callback del helper
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("Camara", "Grabación detenida: ${e.message}")
            // En caso de error, llamar onRecordingStopped() para limpiar UI
            onRecordingStopped()
        }
    }
    
    private fun pausarGrabacion() {
        videoRecordingHelper.pausarGrabacion()
        videoPauseHelper.pausarGrabacion()
    }
    
    private fun reanudarGrabacion() {
        videoRecordingHelper.reanudarGrabacion()
        videoPauseHelper.reanudarGrabacion()
    }
    
    private fun onRecordingStarted() {
        // Inicializar estado de pausa
        videoPauseHelper.resetPauseState()
        videoTimerHelper.setTiempoInicio(System.currentTimeMillis())
        videoTimerHelper.iniciarTemporizador()
        
        // Cambiar estilo del contador a rojo (grabando)
        binding.temporizador.background = getDrawable(R.drawable.contador_background)
        binding.temporizador.setTextColor(getColor(android.R.color.white))
        
        // Deshabilitar botón rotar cámara durante grabación
        binding.btnRotarCamara.isEnabled = false
        binding.btnRotarCamara.alpha = 0.5f
        
        // Habilitar botón de pausar
        binding.btnPausar.isEnabled = true
        binding.btnPausar.alpha = 1.0f
        
        // Durante grabación, mantener botón de grabar habilitado para detener
        binding.btnGrabar.isEnabled = true
        binding.btnGrabar.alpha = 1.0f
        
        // Cambiar texto de indicaciones
        binding.textIndicaciones.text = "Grabando... Mantén tus manos visibles"
        
        // Actualizar botón de pausar
        videoPauseHelper.resetPauseState()
        
        Log.i("GrabarVideo", "Grabación iniciada - Botón habilitado para detener")
    }
    
    private fun onRecordingStopped() {
        // Restaurar botón rotar cámara
        binding.btnRotarCamara.isEnabled = true
        binding.btnRotarCamara.alpha = 1.0f
        
        // Deshabilitar botón de pausar
        binding.btnPausar.isEnabled = false
        binding.btnPausar.alpha = 0.5f
        
        // Restaurar control del botón de grabar basado en posición
        updateRecordButtonState()
        
        // Detener temporizador
        videoTimerHelper.resetTemporizador()
        
        // Restaurar estilo del contador a rojo (estado inicial)
        binding.temporizador.background = getDrawable(R.drawable.contador_background)
        binding.temporizador.setTextColor(getColor(android.R.color.white))
        
        // Restaurar estado de pausa completamente
        videoPauseHelper.resetPauseState()
        
        Log.i("GrabarVideo", "Grabación detenida - Control de posición restaurado")
        
        // Ocultar la vista de cámara y mostrar solo el diálogo de carga
        binding.previewView.visibility = android.view.View.GONE
        binding.fondoCamara.visibility = android.view.View.GONE
        
        // Enviar video a API
        val path = videoRecordingHelper.getRecordingPath()
        if (path != null) {
            videoFileHelper.enviarVideoAPI(path)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionHelper.handlePermissionResult(requestCode, grantResults)) {
            videoRecordingHelper.iniciarCamara()
        } else {
            finish()
        }
    }

    fun restaurarVistaCamara() {
        binding.previewView.visibility = android.view.View.VISIBLE
        binding.fondoCamara.visibility = android.view.View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        // Pausar validación de posición cuando la actividad está en segundo plano
        positionValidator.stopValidation()
    }

    override fun onResume() {
        super.onResume()
        // Reanudar validación de posición cuando la actividad está activa
        positionValidator.startValidation()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Limpiar recursos de forma silenciosa
            videoRecordingHelper.cleanup()
            videoTimerHelper.cleanup()
            // Limpiar estado de pausa completamente
            videoPauseHelper.limpiarCompletamente()
            // Limpiar validador de posición
            positionValidator.stopValidation()
            positionBanner.cleanup()
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("Camara", "Limpieza completada: ${e.message}")
        }
    }
}
