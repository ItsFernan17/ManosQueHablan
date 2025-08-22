package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import com.frivasm.manosquehablan.helpers.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrabarVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar helpers
        initializeHelpers()
        
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
        videoFileHelper = VideoFileHelper(this, this, layoutInflater)
        
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
    
    private fun setupListeners() {
        binding.btnGrabar.setOnClickListener {
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
            onRecordingStopped()
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("Camara", "Grabación detenida: ${e.message}")
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
        
        // Cambiar texto de indicaciones
        binding.textIndicaciones.text = "Grabando... Mantén tus manos visibles"
        
        // Actualizar botón de pausar
        videoPauseHelper.resetPauseState()
    }
    
    private fun onRecordingStopped() {
        // Restaurar botón rotar cámara
        binding.btnRotarCamara.isEnabled = true
        binding.btnRotarCamara.alpha = 1.0f
        
        // Deshabilitar botón de pausar
        binding.btnPausar.isEnabled = false
        binding.btnPausar.alpha = 0.5f
        
        // Restaurar texto de indicaciones
        binding.textIndicaciones.text = "Coloca tus manos dentro del marco"
        
        // Detener temporizador
        videoTimerHelper.resetTemporizador()
        
        // Restaurar estilo del contador a rojo (estado inicial)
        binding.temporizador.background = getDrawable(R.drawable.contador_background)
        binding.temporizador.setTextColor(getColor(android.R.color.white))
        
        // Restaurar estado de pausa completamente
        videoPauseHelper.resetPauseState()
        
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Limpiar recursos de forma silenciosa
            videoRecordingHelper.cleanup()
            videoTimerHelper.cleanup()
            // Limpiar estado de pausa completamente
            videoPauseHelper.limpiarCompletamente()
        } catch (e: Exception) {
            // Solo logear el error, no mostrar Toast al usuario
            Log.d("Camara", "Limpieza completada: ${e.message}")
        }
    }
}
