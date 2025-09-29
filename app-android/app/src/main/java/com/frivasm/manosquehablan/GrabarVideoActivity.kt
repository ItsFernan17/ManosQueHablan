package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import com.frivasm.manosquehablan.dialogs.DialogUtils
import com.frivasm.manosquehablan.helpers.*
import com.frivasm.manosquehablan.ui.PositionValidationBanner
import com.frivasm.manosquehablan.ui.SmoothPositionModal
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalCamera2Interop
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
    private lateinit var smoothPositionModal: SmoothPositionModal
    private var isRecordingAllowed = false
    private var isManualRestart = false // Bandera para reinicio manual


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrabarVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar helpers
        initializeHelpers()
        
        // Inicializar validador de posición ANTES de verificar permisos
        initializePositionValidator()
        
        // Configurar listeners
        setupListeners()
        
        // Verificar permisos
        permissionHelper.verificarPermisos()

        if (permissionHelper.allPermissionsGranted()) {
            videoRecordingHelper.iniciarCamara()
        }

        // Configurar manejo moderno del botón back
        configurarBackPressedCallback()
        
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
        
        // Configurar callback para el límite de tiempo
        videoTimerHelper.onTiempoLimiteAlcanzado = {
            // Detener automáticamente la grabación cuando se alcance 1 minuto
            if (videoRecordingHelper.isRecording()) {
                detenerGrabacion()
            }
        }
        
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

        videoRecordingHelper.onCameraError = { error ->
            // Mostrar error de cámara al usuario
            Log.e("GrabarVideo", "Error de cámara: $error")
            mostrarDialogoErrorCamara(error)
        }
        
        // Configurar callback de exposición para mostrar información sutil
        videoRecordingHelper.exposureControlHelper.onExposureChanged = { luma, evCompensation, torchEnabled ->
            updateExposureIndicator(luma, evCompensation, torchEnabled)
        }

        // Configurar callback para feedback inmediato al tocar pantalla
        videoRecordingHelper.exposureControlHelper.onTouchFeedback = { changeAmount, newEv, cameraType ->
            showTouchBrightnessFeedback(changeAmount, newEv, cameraType)
        }
    }
    
    private fun initializePositionValidator() {
        // Inicializar banner de validación de posición (solo para sin giroscopio)
        positionBanner = PositionValidationBanner(this, binding.guiaManos)
        
        // Inicializar modal suave para validación
        smoothPositionModal = SmoothPositionModal(this, binding.handGuideFrame)
        
        // Inicializar validador de posición
        positionValidator = PositionValidator(
            context = this,
            onPositionChanged = { state, deviation, uxAngle, hasGyroscope ->
                // Usar modal suave para dispositivos con giroscopio
                if (hasGyroscope) {
                    smoothPositionModal.updatePosition(state, deviation, uxAngle, hasGyroscope)
                } else {
                    // Solo usar banner para dispositivos sin giroscopio
                    positionBanner.updatePosition(state, deviation, uxAngle, hasGyroscope)
                }
                // Mantener actualización del texto de indicaciones para consistencia
                updateIndicationsText(state, uxAngle, hasGyroscope)
            },
            onRecordingAllowed = { allowed ->
                // Actualizar directamente el estado del botón de grabar
                isRecordingAllowed = allowed
                updateRecordButtonState()
            }
        )
        
        // Establecer la referencia del validador en la modal para que pueda notificar cuando termine
        smoothPositionModal.positionValidator = positionValidator
        
        // Configurar callback opcional (puede ser útil para logs o otros propósitos)
        smoothPositionModal.onVerificationComplete = {
            Log.i("GrabarVideo", "Verificación de ángulo completada desde modal")
        }
        
        // Iniciar validación de posición INMEDIATAMENTE
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
                // Durante grabación, mostrar mensaje especial si se mueve
                if (hasGyroscope && state != PositionValidator.PositionState.GREEN) {
                    binding.textIndicaciones.text = "Grabando... Si la orientación no está bien, no afecta la traducción"
                } else {
                    binding.textIndicaciones.text = "Grabando... Mantén tus manos visibles"
                }
                return@runOnUiThread
            }
            
            // Transición suave de mensajes según el estado
            val newText = when (state) {
                PositionValidator.PositionState.GREEN -> {
                    "Coloca tus manos dentro del marco"
                }
                PositionValidator.PositionState.RED -> {
                    if (hasGyroscope) {
                        "Endereza el teléfono (ideal 70-90°) - Actual: ${String.format("%.0f", uxAngle)}°"
                    } else {
                        "Sin giroscopio: coloca el teléfono verticalmente"
                    }
                }
                PositionValidator.PositionState.CRITICAL -> {
                    if (hasGyroscope) {
                        "¡Posición crítica! Pon el teléfono en vertical"
                    } else {
                        "Sin giroscopio: coloca el teléfono verticalmente"
                    }
                }
            }
            
            // Solo actualizar si el texto cambió para evitar parpadeos
            if (binding.textIndicaciones.text.toString() != newText) {
                binding.textIndicaciones.text = newText
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
    
    private fun updateExposureIndicator(luma: Float, evCompensation: Int, torchEnabled: Boolean) {
        runOnUiThread {
            val indicator = binding.exposureIndicator
            
            // Verificar soporte del dispositivo
            val hasEvSupport = videoRecordingHelper.exposureControlHelper.isExposureSupported()
            val hasTorchSupport = videoRecordingHelper.exposureControlHelper.isTorchSupported()
            
            // Solo mostrar durante la grabación o si hay ajustes significativos
            val shouldShow = videoRecordingHelper.isRecording() || 
                           (hasEvSupport && evCompensation != 0) || 
                           (hasTorchSupport && torchEnabled) || 
                           luma < 0.3f || 
                           luma > 0.7f
            
            if (shouldShow) {
                indicator.visibility = android.view.View.VISIBLE
                
                // Actualizar texto basado en estado y soporte
                val text = when {
                    torchEnabled && hasTorchSupport -> "ON"
                    evCompensation > 0 && hasEvSupport -> "+$evCompensation"
                    evCompensation < 0 && hasEvSupport -> "$evCompensation"
                    luma < 0.3f && !hasEvSupport -> "◐" // Oscuro pero sin control EV
                    luma > 0.7f && !hasEvSupport -> "◑" // Brillante pero sin control EV
                    luma < 0.3f -> "◐"
                    luma > 0.7f -> "◑"
                    !hasEvSupport -> "○" // Modo solo medición
                    else -> "●"
                }
                
                // Color y mensaje contextual basado en calidad de exposición
                val color: Int
                val contextMessage: String
                
                when {
                    !hasEvSupport -> {
                        color = getColor(android.R.color.holo_blue_light)
                        contextMessage = if (luma < 0.35f) "Busca mejor luz" else "Solo medición"
                    }
                    luma >= 0.47f && luma <= 0.60f -> {
                        color = getColor(android.R.color.holo_green_light)
                        contextMessage = "Iluminación óptima"
                    }
                    luma >= 0.35f && luma <= 0.70f -> {
                        color = getColor(android.R.color.holo_orange_light)
                        contextMessage = "Sistema ajustando"
                    }
                    luma < 0.35f -> {
                        color = getColor(android.R.color.holo_red_light)
                        contextMessage = "Necesitas más luz"
                    }
                    else -> {
                        color = getColor(android.R.color.holo_red_light)
                        contextMessage = "Evita luz directa"
                    }
                }
                
                indicator.text = text
                indicator.setTextColor(color)
                
                // Actualizar las indicaciones con contexto de iluminación
                if (!videoRecordingHelper.isRecording()) {
                    updateIndicationsWithExposureContext(contextMessage, luma, hasEvSupport)
                }
                
            } else {
                indicator.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun updateIndicationsWithExposureContext(contextMessage: String, luma: Float, hasEvSupport: Boolean) {
        val currentText = binding.textIndicaciones.text.toString()

        // Solo actualizar si no estamos en un mensaje crítico de posición
        if (!currentText.contains("¡Posición crítica!") && !currentText.contains("Endereza el teléfono")) {
            val baseMessage = "Coloca tus manos dentro del marco"
            val exposureHint = when {
                luma < 0.35f && !hasEvSupport -> "Busca mejor luz ambiente"
                luma < 0.35f && hasEvSupport -> "Sistema mejorando brillo automáticamente"
                luma > 0.7f -> "Evita luz directa o reflectores"
                luma >= 0.47f && luma <= 0.60f -> "Iluminación perfecta"
                else -> contextMessage
            }

            // Mostrar el hint de exposición primero, luego el mensaje base
            binding.textIndicaciones.text = if (exposureHint.isNotEmpty() && exposureHint != "Solo medición") {
                "$exposureHint\n$baseMessage"
            } else {
                baseMessage
            }
        }
    }

    private fun showTouchBrightnessFeedback(changeAmount: Int, newEv: Int, cameraType: String) {
        runOnUiThread {
            val indicator = binding.exposureIndicator

            // Mostrar indicador inmediatamente
            indicator.visibility = android.view.View.VISIBLE

            // Determinar texto basado en el cambio
            val feedbackText = when {
                changeAmount > 0 -> "+$changeAmount"
                changeAmount < 0 -> "$changeAmount"
                else -> "●" // Sin cambio
            }

            // Color basado en el cambio
            val color = when {
                changeAmount > 0 -> getColor(android.R.color.holo_green_light) // Más brillo
                changeAmount < 0 -> getColor(android.R.color.holo_red_light)   // Menos brillo
                else -> getColor(android.R.color.holo_blue_light)             // Sin cambio
            }

            // Actualizar indicador
            indicator.text = feedbackText
            indicator.setTextColor(color)

            // Mostrar información adicional en las indicaciones
            val brightnessMessage = when {
                changeAmount > 0 -> "Brillo aumentado (+$changeAmount) - $cameraType"
                changeAmount < 0 -> "Brillo reducido ($changeAmount) - $cameraType"
                else -> "Sin ajuste de brillo necesario - $cameraType"
            }

            // Solo mostrar si no estamos grabando
            if (!videoRecordingHelper.isRecording()) {
                val currentText = binding.textIndicaciones.text.toString()
                if (!currentText.contains("¡Posición crítica!") && !currentText.contains("Endereza el teléfono")) {
                    binding.textIndicaciones.text = brightnessMessage
                    // Restaurar mensaje original después de 2 segundos
                    binding.textIndicaciones.postDelayed({
                        if (!videoRecordingHelper.isRecording()) {
                            binding.textIndicaciones.text = "Coloca tus manos dentro del marco"
                        }
                    }, 2000)
                }
            }

            // Ocultar indicador después de 3 segundos si no hay más actividad
            indicator.postDelayed({
                if (!videoRecordingHelper.isRecording() &&
                    videoRecordingHelper.exposureControlHelper.getCurrentEvCompensation() == newEv) {
                    indicator.visibility = android.view.View.GONE
                }
            }, 3000)

            Log.d("GrabarVideo", "Feedback de brillo: cambio=$changeAmount, EV=$newEv, cámara=$cameraType")
        }
    }
    
    private fun setupListeners() {
        binding.btnGrabar.setOnClickListener {
            // Mostrar banner inmediatamente para dar feedback visual instantáneo
            if (positionValidator.hasGyroscope && !isRecordingAllowed && !videoRecordingHelper.isRecording()) {
                smoothPositionModal.forceShowBannerOnButtonPress()
            }
            
            // Verificar posición antes de permitir grabar
            if (!isRecordingAllowed && !videoRecordingHelper.isRecording()) {
                Log.w("GrabarVideo", "Intento de grabación bloqueado - Posición incorrecta")
                // El banner ya muestra el mensaje, no necesitamos Toast
                return@setOnClickListener
            }
            
            // Deshabilitar temporalmente el botón para evitar doble clic
            binding.btnGrabar.isEnabled = false
            
            if (videoRecordingHelper.isRecording()) {
                detenerGrabacion()
                // El botón se habilitará en onRecordingStopped() según la posición
            } else {
                iniciarGrabacion()
                // El botón se habilitará en onRecordingStarted()
            }
            
            // Habilitar el botón después de un pequeño retraso como precaución
            binding.btnGrabar.postDelayed({
                if (videoRecordingHelper.isRecording()) {
                    binding.btnGrabar.isEnabled = true
                } else {
                    updateRecordButtonState()
                }
            }, 1000) // 1 segundo de retraso para evitar clics accidentales
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

        // Long click en btnRotarCamara - funcionalidad removida
        // binding.btnRotarCamara.setOnLongClickListener { true }
        
        binding.btnCerrar.setOnClickListener {
            try {
                if (videoRecordingHelper.isRecording()) {
                    // Cancelar grabación (no enviar video)
                    isManualRestart = true
                    videoRecordingHelper.detenerGrabacion()
                }
                finish()
            } catch (e: Exception) {
                Log.e("Camara", "Error al cerrar: ${e.message}")
                finish()
            }
        }
        
    }
    
    private fun iniciarGrabacion() {
        Log.d("GrabarVideo", "Iniciando grabación...")
        // Ir directamente a la grabación sin mostrar modales
        procederConGrabacion()
    }

    private fun procederConGrabacion() {
        videoRecordingHelper.iniciarGrabacion()
    }

    
    private fun detenerGrabacion() {
        try {
            // Asegurar que NO es un reinicio manual (detención normal)
            isManualRestart = false
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
    
    private fun reiniciarVideo() {
        try {
            Log.i("GrabarVideo", "Reiniciando video - Eliminar y empezar de nuevo")
            
            // Marcar que es un reinicio manual para evitar envío al servidor
            isManualRestart = true
            
            // Detener grabación actual si está activa
            if (videoRecordingHelper.isRecording()) {
                videoRecordingHelper.detenerGrabacion()
            }
            
            // Resetear helpers de pausa y timer
            videoPauseHelper.resetPauseState()
            videoTimerHelper.resetTemporizador()
            
            // Reiniciar UI a estado inicial
            resetUIToInitialState()
            
            // Log para debugging
            Log.i("GrabarVideo", "Video reiniciado exitosamente - Listo para nueva grabación")
            
        } catch (e: Exception) {
            Log.e("GrabarVideo", "Error al reiniciar video: ${e.message}")
            // En caso de error, intentar resetear UI de todas formas
            resetUIToInitialState()
        } finally {
            // Asegurar que la bandera se resetee
            isManualRestart = false
        }
    }
    
    private fun resetUIToInitialState() {
        runOnUiThread {
            // Resetear temporizador
            binding.temporizador.text = "00:00"
            binding.temporizador.background = getDrawable(R.drawable.contador_background)
            binding.temporizador.setTextColor(getColor(android.R.color.white))
            
            // Resetear botones a estado inicial
            binding.btnGrabar.isEnabled = isRecordingAllowed
            binding.btnGrabar.alpha = if (isRecordingAllowed) 1.0f else 0.5f
            
            binding.btnPausar.isEnabled = false
            binding.btnPausar.alpha = 0.5f
            
            binding.btnRotarCamara.isEnabled = true
            binding.btnRotarCamara.alpha = 1.0f
            
            // Resetear indicaciones de texto
            binding.textIndicaciones.text = "Coloca tus manos dentro del marco"
            
            // Ocultar indicador de exposición si no es necesario
            binding.exposureIndicator.visibility = android.view.View.GONE
            
            Log.d("GrabarVideo", "UI reseteada a estado inicial")
        }
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
        
        // Asegurar que no estamos en estado de grabación
        // No es necesario establecer recording a null aquí, eso se maneja en videoRecordingHelper
        
        // Restaurar control del botón de grabar basado en posición
        // Deshabilitar temporalmente para evitar clics accidentales
        binding.btnGrabar.isEnabled = false
        
        // Detener temporizador
        videoTimerHelper.resetTemporizador()
        
        // Restaurar estilo del contador a rojo (estado inicial)
        binding.temporizador.background = getDrawable(R.drawable.contador_background)
        binding.temporizador.setTextColor(getColor(android.R.color.white))
        
        // Restaurar estado de pausa completamente
        videoPauseHelper.resetPauseState()
        
        Log.i("GrabarVideo", "Grabación detenida - Control de posición restaurado")
        
        // Solo enviar video si NO es un reinicio manual
        if (!isManualRestart) {
            // Ocultar la vista de cámara y mostrar solo el diálogo de carga
            binding.previewView.visibility = android.view.View.GONE
            binding.fondoCamara.visibility = android.view.View.GONE

            // Enviar video a API
            val path = videoRecordingHelper.getRecordingPath()
            if (path != null) {
                videoFileHelper.enviarVideoAPI(path)
            }
        } else {
            // Si es reinicio manual, solo logear y mantener UI
            Log.i("GrabarVideo", "Reinicio manual - Video NO enviado al servidor")
        }

        // Restaurar estado del botón de grabar después de un breve retraso
        binding.btnGrabar.postDelayed({
            updateRecordButtonState()
        }, 500)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionHelper.handlePermissionResult(requestCode, grantResults)) {
            videoRecordingHelper.iniciarCamara()
        } else {
            // Mostrar diálogo explicativo cuando se deniegan permisos
            mostrarDialogoPermisosDenegados()
        }
    }

    private fun mostrarDialogoPermisosDenegados() {
        val deniedPermissions = mutableListOf<String>()

        // Verificar qué permisos fueron denegados
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deniedPermissions.add("Cámara")
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deniedPermissions.add("Micrófono")
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deniedPermissions.add("Acceso a videos")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            deniedPermissions.add("Notificaciones")
        }

        val permissionsText = deniedPermissions.joinToString(", ")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permisos requeridos")
            .setMessage("La aplicación necesita los siguientes permisos para funcionar correctamente: $permissionsText.\n\nPuedes conceder los permisos desde la configuración de la aplicación.")
            .setPositiveButton("Ir a configuración") { _, _ ->
                // Abrir configuración de la app
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Salir") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun mostrarDialogoErrorCamara(errorMessage: String) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Error de cámara")
                .setMessage("No se pudo inicializar la cámara: $errorMessage\n\nPosibles soluciones:\n• Verifica que la cámara no esté siendo usada por otra aplicación\n• Reinicia el dispositivo\n• Verifica que la cámara funcione correctamente")
                .setPositiveButton("Reintentar") { _, _ ->
                    // Reintentar inicializar la cámara
                    if (permissionHelper.allPermissionsGranted()) {
                        videoRecordingHelper.iniciarCamara()
                    }
                }
                .setNegativeButton("Salir") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Manejar cambios de configuración (rotación) sin destruir la actividad
        Log.d("GrabarVideo", "Configuración cambiada - Rotación detectada")

        // Actualizar la vista de cámara si es necesario
        binding.previewView.post {
            // Forzar actualización del layout después de la rotación
            binding.previewView.requestLayout()
        }

        // Si hay una grabación en curso, mantener el estado
        if (videoRecordingHelper.isRecording()) {
            Log.i("GrabarVideo", "Grabación en curso durante rotación - manteniendo estado")
        }
    }

    /**
     * Configura el manejo moderno del botón back usando OnBackPressedCallback
     */
    private fun configurarBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevenir que el usuario salga accidentalmente durante la grabación
                if (videoRecordingHelper.isRecording()) {
                    Log.w("GrabarVideo", "Intento de salir durante grabación - bloqueado")
                    // Mostrar mensaje sutil sin interrumpir la grabación
                    runOnUiThread {
                        binding.textIndicaciones.text = "Grabando... Presiona el botón rojo para detener"
                        binding.textIndicaciones.postDelayed({
                            binding.textIndicaciones.text = "Grabando... Mantén tus manos visibles"
                        }, 3000)
                    }
                    // No hacer nada más - el callback ya maneja el bloqueo
                    return
                }

                // Si no hay grabación, permitir salir normalmente
                isEnabled = false // Deshabilitar temporalmente para permitir el comportamiento por defecto
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true // Re-habilitar
            }
        }

        // Agregar el callback al dispatcher
        onBackPressedDispatcher.addCallback(this, callback)
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
            smoothPositionModal.cleanup()
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("Camara", "Limpieza completada: ${e.message}")
        }
    }
}
