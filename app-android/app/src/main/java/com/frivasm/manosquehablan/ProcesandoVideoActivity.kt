package com.frivasm.manosquehablan

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.activity.OnBackPressedCallback
import com.frivasm.manosquehablan.api.ApiCliente
import com.frivasm.manosquehablan.api.RespuestaProcesamiento
import com.frivasm.manosquehablan.config.ServerConfig
import com.frivasm.manosquehablan.helpers.ConectividadHelper
import com.frivasm.manosquehablan.helpers.NotificationHelper
import com.frivasm.manosquehablan.helpers.VideoStorageManager
import com.frivasm.manosquehablan.helpers.VideoTranslationStatusHelper
import com.frivasm.manosquehablan.dialogs.DialogUtils
import com.frivasm.manosquehablan.workers.VideoWorkManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalCamera2Interop
class ProcesandoVideoActivity : AppCompatActivity() {
    
    // Estado de la actividad
    private var currentVideoPath: String? = null
    private var currentSessionId: String? = null
    private var isApiCallInProgress = false
    
    // Views para la animación de loading
    private lateinit var txtProcesando: TextView
    private lateinit var progressCircle: CircularProgressIndicator
    private lateinit var centerCircle: View
    private lateinit var videoPreview: android.widget.ImageView
    
    // Views para las figuras geométricas animadas (solo círculos)
    private lateinit var circle1: android.widget.ImageView
    private lateinit var circle2: android.widget.ImageView
    private lateinit var circle3: android.widget.ImageView
    private lateinit var circle4: android.widget.ImageView
    private lateinit var circle5: android.widget.ImageView
    private lateinit var circle6: android.widget.ImageView
    private lateinit var circle7: android.widget.ImageView
    private lateinit var circle8: android.widget.ImageView
    private lateinit var circle9: android.widget.ImageView
    private lateinit var circle10: android.widget.ImageView
    private lateinit var circle11: android.widget.ImageView
    private lateinit var circle12: android.widget.ImageView
    private lateinit var circle13: android.widget.ImageView
    private lateinit var circle14: android.widget.ImageView
    private lateinit var circle15: android.widget.ImageView
    private lateinit var circle16: android.widget.ImageView
    private lateinit var circle17: android.widget.ImageView
    private lateinit var circle18: android.widget.ImageView
    
    // Puntos de carga animados
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    
    // Variable para controlar la animación
    private var isAnimating = true
    
    // Variables para WorkManager
    private var isUsingWorkManager = true // Flag para alternar entre implementaciones
    
    // Referencias a animadores para poder cancelarlos
    private val activeAnimators = mutableListOf<ObjectAnimator>()
    private val activeValueAnimators = mutableListOf<ValueAnimator>()
    private val activeAnimatorSets = mutableListOf<AnimatorSet>()
    private val activeHandlers = mutableListOf<Handler>()

    // Overlay para indicar que la navegación está bloqueada
    private lateinit var backBlockOverlay: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_procesando_video)

        // Configurar protección contra navegación hacia atrás durante procesamiento
        configurarProteccionBackButton()

        // Inicializar vistas
        initializeViews()

        // Iniciar animación de loading
        startLoadingAnimation()

        // Obtener el path del video desde el intent
        currentVideoPath = intent.getStringExtra("VIDEO_PATH")
        if (currentVideoPath != null) {
            // Solo usar fondo negro sin miniatura del video
            if (isUsingWorkManager) {
                // NUEVA IMPLEMENTACIÓN: Usar WorkManager
                enviarVideoConWorkManager(currentVideoPath!!)
            } else {
                // IMPLEMENTACIÓN ORIGINAL: Mantener como fallback
                enviarVideoAPI(currentVideoPath!!)
            }
        } else {
            // Cancelar notificación de procesamiento
            NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
            mostrarErrorYRegresarInicio("Error: No se encontró el video a procesar")
        }
    }

    /**
     * Configura protección completa contra navegación hacia atrás durante procesamiento
     */
    private fun configurarProteccionBackButton() {
        // Usar el nuevo sistema de back pressed callback para máxima protección
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val isProcessingActive = isApiCallInProgress || isAnimating || currentSessionId != null

                if (!isProcessingActive) {
                    // Solo permitir regresar si NO hay procesamiento activo
                    isEnabled = false // Deshabilitar este callback
                    onBackPressedDispatcher.onBackPressed() // Usar el comportamiento por defecto
                    isEnabled = true // Re-habilitar
                } else {
                    // Mostrar mensaje y mantener bloqueado
                    runOnUiThread {
                        Toast.makeText(
                            this@ProcesandoVideoActivity,
                            "⏳ Procesamiento en curso. No puedes salir hasta que termine.",
                            Toast.LENGTH_LONG
                        ).show()

                        Log.w("ProcesandoVideoActivity", "BACK BLOCKED - Procesamiento activo: API=$isApiCallInProgress, Animación=$isAnimating, WorkManager=${currentSessionId != null}")
                    }
                }
            }
        }

        // Agregar el callback al dispatcher
        onBackPressedDispatcher.addCallback(this, callback)
    }
    
    private fun initializeViews() {
        // Views principales
        txtProcesando = findViewById(R.id.txtProcesando)
        progressCircle = findViewById(R.id.circularProgressIndicator)
        centerCircle = findViewById(R.id.centerCircle)
        videoPreview = findViewById(R.id.videoPreview)

        // Círculos animados de fondo
        circle1 = findViewById(R.id.circle1)
        circle2 = findViewById(R.id.circle2)
        circle3 = findViewById(R.id.circle3)
        circle4 = findViewById(R.id.circle4)
        circle5 = findViewById(R.id.circle5)
        circle6 = findViewById(R.id.circle6)
        circle7 = findViewById(R.id.circle7)
        circle8 = findViewById(R.id.circle8)
        circle9 = findViewById(R.id.circle9)
        circle10 = findViewById(R.id.circle10)
        circle11 = findViewById(R.id.circle11)
        circle12 = findViewById(R.id.circle12)
        circle13 = findViewById(R.id.circle13)
        circle14 = findViewById(R.id.circle14)
        circle15 = findViewById(R.id.circle15)
        circle16 = findViewById(R.id.circle16)
        circle17 = findViewById(R.id.circle17)
        circle18 = findViewById(R.id.circle18)

        // Puntos de carga animados
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        // Inicializar overlay de bloqueo de navegación
        inicializarOverlayBloqueo()
    }

    /**
     * Inicializa el overlay visual que indica que la navegación está bloqueada
     */
    private fun inicializarOverlayBloqueo() {
        // Crear overlay programáticamente
        backBlockOverlay = View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@ProcesandoVideoActivity, R.color.black_overlay_70))
            alpha = 0f // Inicialmente invisible
            visibility = View.GONE
        }

        // Agregar al layout raíz
        val rootLayout = findViewById<View>(android.R.id.content)
        if (rootLayout is android.widget.FrameLayout) {
            rootLayout.addView(backBlockOverlay, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun startLoadingAnimation() {
        isAnimating = true

        // Mostrar overlay de bloqueo de navegación
        mostrarOverlayBloqueo()

        // Iniciar animaciones de los círculos de fondo
        startCirclesAnimation()

        // Iniciar animación del círculo de progreso
        startProgressCircleAnimation()

        // Iniciar animación de los puntos de carga
        startLoadingDotsAnimation()
    }
    
    private fun stopLoadingAnimation() {
        isAnimating = false

        // Ocultar overlay de bloqueo de navegación
        ocultarOverlayBloqueo()

        // Cancelar todos los animadores activos
        activeAnimators.forEach { it.cancel() }
        activeValueAnimators.forEach { it.cancel() }
        activeAnimatorSets.forEach { it.cancel() }

        // Limpiar handlers pendientes
        activeHandlers.forEach { handler ->
            handler.removeCallbacksAndMessages(null)
        }

        // Limpiar listas
        activeAnimators.clear()
        activeValueAnimators.clear()
        activeAnimatorSets.clear()
        activeHandlers.clear()
    }

    /**
     * Muestra el overlay visual que indica bloqueo de navegación
     */
    private fun mostrarOverlayBloqueo() {
        runOnUiThread {
            backBlockOverlay.visibility = View.VISIBLE
            backBlockOverlay.animate()
                .alpha(0.1f) // Muy sutil, apenas visible
                .setDuration(300)
                .start()
        }
    }

    /**
     * Oculta el overlay de bloqueo de navegación
     */
    private fun ocultarOverlayBloqueo() {
        runOnUiThread {
            backBlockOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    backBlockOverlay.visibility = View.GONE
                }
                .start()
        }
    }
    
    private fun startCirclesAnimation() {
        val circles = listOf(
            circle1, circle2, circle3, circle4, circle5, circle6, circle7, circle8, circle9,
            circle10, circle11, circle12, circle13, circle14, circle15, circle16, circle17, circle18
        )
        
        circles.forEachIndexed { index, circle ->
            // Animación de rotación suave
            val rotateAnimator = ObjectAnimator.ofFloat(circle, "rotation", 0f, 360f).apply {
                duration = (3000 + (index * 200)).toLong() // Diferentes velocidades
                repeatCount = ObjectAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
            }
            
            // Animación de escala (respiración)
            val scaleX = ObjectAnimator.ofFloat(circle, "scaleX", 1f, 1.3f, 1f).apply {
                duration = (2000 + (index * 150)).toLong()
                repeatCount = ObjectAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            val scaleY = ObjectAnimator.ofFloat(circle, "scaleY", 1f, 1.3f, 1f).apply {
                duration = (2000 + (index * 150)).toLong()
                repeatCount = ObjectAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            // Animación de alpha (parpadeo suave)
            val alphaAnimator = ObjectAnimator.ofFloat(circle, "alpha", circle.alpha, circle.alpha * 0.6f, circle.alpha).apply {
                duration = (1500 + (index * 100)).toLong()
                repeatCount = ObjectAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            // Almacenar referencias para poder cancelar
            activeAnimators.addAll(listOf(rotateAnimator, scaleX, scaleY, alphaAnimator))
            
            // Pequeño delay inicial para cada círculo
            val handler = Handler(Looper.getMainLooper())
            activeHandlers.add(handler)
            
            handler.postDelayed({
                if (isAnimating) {
                    rotateAnimator.start()
                    scaleX.start()
                    scaleY.start()
                    alphaAnimator.start()
                }
            }, (index * 200).toLong())
        }
    }
    
    private fun startProgressCircleAnimation() {
        // Activar modo indeterminado para rotación continua
        progressCircle.isIndeterminate = true
        
        // Animación sutil de pulsación para el círculo central
        val scaleX = ObjectAnimator.ofFloat(centerCircle, "scaleX", 1.0f, 1.05f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        
        val scaleY = ObjectAnimator.ofFloat(centerCircle, "scaleY", 1.0f, 1.05f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        
        if (isAnimating) {
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(scaleX, scaleY)
            
            // Almacenar referencias para poder cancelar
            activeAnimators.addAll(listOf(scaleX, scaleY))
            activeAnimatorSets.add(animatorSet)
            
            animatorSet.start()
        }
    }
    
    private fun startLoadingDotsAnimation() {
        val dots = listOf(dot1, dot2, dot3)
        
        dots.forEachIndexed { index, dot ->
            // Animación de salto (bounce) - movimiento vertical hacia arriba y abajo
            val jumpUp = ObjectAnimator.ofFloat(dot, "translationY", 0f, -25f).apply {
                duration = 350
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            val fallDown = ObjectAnimator.ofFloat(dot, "translationY", -25f, 0f).apply {
                duration = 350
                interpolator = android.view.animation.BounceInterpolator()
            }
            
            // Secuencia de salto completa
            val jumpSequence = AnimatorSet().apply {
                playSequentially(jumpUp, fallDown)
            }
            
            // Animación de escala durante el salto para más dramatismo
            val scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.3f, 1f).apply {
                duration = 700 // Misma duración que el salto completo
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            val scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.3f, 1f).apply {
                duration = 700
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            // Animación de brillo/glow durante el salto
            val alpha = ObjectAnimator.ofFloat(dot, "alpha", 0.7f, 1f, 0.7f).apply {
                duration = 700
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            
            // Crear animador que repite el salto
            val bounceAnimator = AnimatorSet().apply {
                playTogether(jumpSequence, scaleX, scaleY, alpha)
                startDelay = (index * 300).toLong() // Delay entre puntos para efecto de onda
            }
            
            // Almacenar referencias para poder cancelar
            activeAnimators.addAll(listOf(jumpUp, fallDown, scaleX, scaleY, alpha))
            activeAnimatorSets.add(jumpSequence)
            
            // Crear un AnimatorSet que se repite infinitamente
            val infiniteBounceAnimator = AnimatorSet().apply {
                playTogether(jumpSequence, scaleX, scaleY, alpha)
                startDelay = (index * 300).toLong()
            }
            
            // Configurar la repetición usando un animador con listener
            infiniteBounceAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAnimating) {
                        // Reiniciar la animación después de un pequeño delay
                        val handler = Handler(Looper.getMainLooper())
                        activeHandlers.add(handler)
                        handler.postDelayed({
                            if (isAnimating) {
                                infiniteBounceAnimator.start()
                            }
                        }, 200) // 200ms de pausa entre ciclos
                    }
                }
            })
            
            activeAnimatorSets.add(infiniteBounceAnimator)
            
            if (isAnimating) {
                infiniteBounceAnimator.start()
            }
        }
    }
    
    /**
     * NUEVA IMPLEMENTACIÓN: Usar WorkManager para procesamiento robusto
     */
    private fun enviarVideoConWorkManager(videoPath: String) {
        // Verificar que no haya otra llamada en progreso
        if (isApiCallInProgress) {
            Log.d("ProcesandoVideoActivity", "Procesamiento ya en progreso, ignorando...")
            return
        }

        isApiCallInProgress = true
        Log.i("ProcesandoVideoActivity", "Iniciando procesamiento con WorkManager: $videoPath")

        lifecycleScope.launch {
            try {
                // 1. Verificar conectividad del servidor ANTES de encolar el trabajo
                val conectividadHelper = ConectividadHelper(this@ProcesandoVideoActivity)

                // Mostrar mensaje de verificación
                runOnUiThread {
                    txtProcesando.text = getString(R.string.verificando_servidor)
                }

                val estadoServidor = conectividadHelper.verificarServidorConReintentos { mensaje ->
                    runOnUiThread {
                        txtProcesando.text = mensaje
                    }
                }

                if (!estadoServidor.esDisponible) {
                    Log.w("ProcesandoVideoActivity", "Servidor no disponible: ${estadoServidor.mensaje}")
                    mostrarErrorConectividad(estadoServidor, videoPath)
                    return@launch
                }

                // 2. Si el servidor está disponible, proceder con WorkManager
                Log.i("ProcesandoVideoActivity", "Servidor disponible. Encolando trabajo con WorkManager...")
                runOnUiThread {
                    txtProcesando.text = "Procesando tu video, por favor espera..."
                }

                // Encolar trabajo de procesamiento
                currentSessionId = VideoWorkManager.enqueueVideoProcessing(this@ProcesandoVideoActivity, videoPath)
                Log.i("ProcesandoVideoActivity", "Trabajo encolado con sesión: $currentSessionId")

                // Observar progreso del trabajo
                observarProgresoWorker()

            } catch (e: Exception) {
                Log.e("ProcesandoVideoActivity", "Error iniciando WorkManager: ${e.message}")
                // Cancelar notificación de procesamiento
                NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
                isApiCallInProgress = false
                mostrarErrorYRegresarInicio("Error iniciando el procesamiento: ${e.message}")
            }
        }
    }
    
    /**
     * Observa el progreso del Worker y actualiza la UI
     */
    private fun observarProgresoWorker() {
        val sessionId = currentSessionId ?: return
        
        VideoWorkManager.observeWorkProgress(this, sessionId) { workInfo ->
            runOnUiThread {
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        // Obtener progreso detallado del Worker
                        val progress = workInfo.progress
                        val state = progress.getString("state") ?: "procesando"
                        val message = progress.getString("message") ?: "Procesando tu video..."
                        
                        txtProcesando.text = message
                        Log.d("ProcesandoVideoActivity", "Trabajo ejecutándose: $state - $message")
                    }
                    
                    WorkInfo.State.SUCCEEDED -> {
                        Log.i("ProcesandoVideoActivity", "Trabajo completado exitosamente")
                        txtProcesando.text = "¡Traducción completada!"

                        // Detener animación
                        stopLoadingAnimation()
                        isApiCallInProgress = false


                        // NO abrir la app automáticamente - dejar que el usuario toque la notificación
                        // Solo reset flag después de un delay para que vea el mensaje
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                isApiCallInProgress = false // Reset flag
                                finish()
                            }
                        }, 1500)
                    }
                    
                    WorkInfo.State.FAILED -> {
                        Log.w("ProcesandoVideoActivity", "Trabajo falló")
                        // Cancelar notificación de procesamiento
                        NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
                        stopLoadingAnimation()
                        isApiCallInProgress = false
                        
                        // Obtener información del error del progress data
                        val progress = workInfo.progress
                        val state = progress.getString("state") ?: "error"
                        val errorMessage = progress.getString("message") ?: "Error procesando el video. Intenta de nuevo."
                        
                        Log.w("ProcesandoVideoActivity", "Error state: $state, message: $errorMessage")
                        
                        // Determinar si es error de conectividad o servidor
                        when {
                            errorMessage.contains("Sin conexión", ignoreCase = true) ||
                            errorMessage.contains("conexión", ignoreCase = true) -> {
                                // Error de conectividad - usar diálogo específico
                                mostrarErrorConectividad(ServerConfig.ServerStatus.SIN_CONEXION, currentVideoPath ?: "")
                            }
                            errorMessage.contains("Error del servidor", ignoreCase = true) ||
                            errorMessage.contains("500", ignoreCase = true) -> {
                                // Error del servidor
                                mostrarErrorYRegresarInicio("Error del servidor. El servicio está temporalmente fuera de línea. Intenta más tarde.")
                            }
                            else -> {
                                // Error genérico
                                mostrarErrorYRegresarInicio(errorMessage)
                            }
                        }
                    }
                    
                    WorkInfo.State.CANCELLED -> {
                        Log.w("ProcesandoVideoActivity", "Trabajo cancelado")
                        // Cancelar notificación de procesamiento
                        NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
                        stopLoadingAnimation()
                        isApiCallInProgress = false
                        mostrarErrorYRegresarInicio("Procesamiento cancelado")
                    }
                    
                    else -> {
                        Log.d("ProcesandoVideoActivity", "Estado del trabajo: ${workInfo?.state}")
                    }
                }
            }
        }
    }

    

    /**
     * IMPLEMENTACIÓN ORIGINAL: Mantener como fallback
     */
    private fun enviarVideoAPI(path: String) {
        // Verificar que no haya otra llamada en progreso
        if (isApiCallInProgress) {
            Log.d("ProcesandoVideoActivity", "Llamada al API ya en progreso, ignorando...")
            return
        }
        
        isApiCallInProgress = true
        Log.d("ProcesandoVideoActivity", "Iniciando verificación de conectividad...")
        
        lifecycleScope.launch {
            try {
                // 1. Verificar conectividad del servidor ANTES de enviar el video
                val conectividadHelper = ConectividadHelper(this@ProcesandoVideoActivity)
                
                // Mostrar mensaje de verificación
                runOnUiThread {
                    txtProcesando.text = getString(R.string.verificando_servidor)
                }
                
                val estadoServidor = conectividadHelper.verificarServidorConReintentos { mensaje ->
                    runOnUiThread {
                        txtProcesando.text = mensaje
                    }
                }
                
                if (!estadoServidor.esDisponible) {
                    Log.w("ProcesandoVideoActivity", "Servidor no disponible: ${estadoServidor.mensaje}")
                    mostrarErrorConectividad(estadoServidor, path)
                    return@launch
                }
                
                // 2. Si el servidor está disponible, proceder con el envío
                Log.i("ProcesandoVideoActivity", "Servidor disponible. Enviando video...")
                runOnUiThread {
                    txtProcesando.text = "Procesando tu video, por favor espera..."
                }
                
                enviarVideoAlServidor(path)
                
            } catch (e: Exception) {
                Log.e("ProcesandoVideoActivity", "Excepción durante verificación: ${e.message}")
                isApiCallInProgress = false
                
                // Clasificar el error y mostrar el diálogo apropiado
                val estadoError = when (e) {
                    is java.net.ConnectException -> ServerConfig.ServerStatus.SIN_CONEXION
                    is java.net.SocketTimeoutException -> ServerConfig.ServerStatus.TIMEOUT
                    else -> ServerConfig.ServerStatus.ERROR_DESCONOCIDO
                }
                
                mostrarErrorConectividad(estadoError, path)
            }
        }
    }
    
    /**
     * Método original de envío al servidor, ahora separado
     */
    private suspend fun enviarVideoAlServidor(path: String) {
        val videoFile = File(path)
        val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

        try {
            val response = withContext(Dispatchers.IO) {
                ApiCliente.instance.procesarVideo(body)
            }
            
            Log.d("ProcesandoVideoActivity", "Respuesta del API: ${response.code()} - ${response.message()}")
            
            if (response.isSuccessful && response.body() != null) {
                Log.d("ProcesandoVideoActivity", "API exitoso, validando respuesta...")
                val data = response.body()!!
                
                // Actualizar mensaje de progreso
                runOnUiThread {
                    txtProcesando.text = "Descargando archivos traducidos..."
                }
                
                // Validar que todos los archivos necesarios estén disponibles
                if (data.video_url.isNullOrBlank()) {
                    Log.e("ProcesandoVideoActivity", "El servidor no proporcionó URL del video")
                    // Cancelar notificación de procesamiento
                    NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
                    mostrarErrorYRegresarInicio("Error: el servidor no generó el video")
                    return
                }
                
                if (data.audio_url.isNullOrBlank()) {
                    Log.e("ProcesandoVideoActivity", "PROBLEMA DEL SERVIDOR: No proporcionó URL del audio")
                    // Marcar con etiqueta de error de servidor en lugar de mal traducido
                    val videoPath = intent.getStringExtra("VIDEO_PATH")
                    videoPath?.let { path ->
                        val videoFile = File(path)
                        VideoTranslationStatusHelper.marcarVideoConErrorServidor(videoFile)
                        Log.d("ProcesandoVideoActivity", "Video marcado con ERROR DE SERVIDOR (audio faltante)")
                    }
                    // Cancelar notificación de procesamiento
                    NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
                    mostrarErrorYRegresarInicio("Error del servidor: no se generó el archivo de audio")
                    return
                }
                
                if (data.texto_url.isNullOrBlank()) {
                    Log.e("ProcesandoVideoActivity", "PROBLEMA DEL SERVIDOR: No proporcionó URL del texto")
                    // Marcar con etiqueta de error de servidor en lugar de mal traducido
                    val videoPath = intent.getStringExtra("VIDEO_PATH")
                    videoPath?.let { path ->
                        val videoFile = File(path)
                        VideoTranslationStatusHelper.marcarVideoConErrorServidor(videoFile)
                        Log.d("ProcesandoVideoActivity", "Video marcado con ERROR DE SERVIDOR (texto faltante)")
                    }
                    // Cancelar notificación de procesamiento
                    NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
                    mostrarErrorYRegresarInicio("Error del servidor: no se generó el archivo de texto")
                    return
                }
                
                try {
                    Log.d("ProcesandoVideoActivity", "Todos los archivos disponibles, guardando...")
                    val esMalTraducido = guardarArchivosEnCarpeta(data)
                    
                    // Detener animación
                    stopLoadingAnimation()
                    
                    if (esMalTraducido) {
                        // Video marcado como mal traducido pero archivos guardados
                        Log.w("ProcesandoVideoActivity", "Video completado pero marcado como mal traducido")
                        Log.d("ProcesandoVideoActivity", "Video procesado con error - enviando notificación...")
                        
                        // Regresar al inicio sin notificación duplicada
                        runOnUiThread {
                            stopLoadingAnimation()
                            Log.d("ProcesandoVideoActivity", "Video mal traducido - sin notificación adicional")

                            // Regresar al inicio
                            mostrarErrorYRegresarInicio("Video guardado pero requiere nueva traducción")
                        }
                    } else {
                        // Video completamente normal - mostrar notificación de éxito
                        runOnUiThread {
                            // Cancelar notificación de procesamiento y mostrar éxito
                            mostrarNotificacionFinalUnificada(true)

                            // Mostrar toast de éxito
                            Toast.makeText(
                                this@ProcesandoVideoActivity,
                                "Video guardado correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        // NO abrir la app automáticamente - dejar que el usuario toque la notificación
                        // Solo reset flag y finish después de un delay para que vea el toast
                        delay(1500) // Dar tiempo para que el usuario vea el toast
                        isApiCallInProgress = false // Reset flag
                        finish()
                    }
                    
                } catch (e: Exception) {
                    Log.e("ProcesandoVideoActivity", "Error al guardar archivos: ${e.message}")
                    isApiCallInProgress = false // Reset flag
                    mostrarErrorYRegresarInicio("Error al descargar los archivos del servidor")
                }
            } else {
                Log.e("ProcesandoVideoActivity", "API no exitoso: ${response.code()} - ${response.message()}")

                // Cancelar notificación de procesamiento
                NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)

                isApiCallInProgress = false // Reset flag

                // Mostrar error dialog según el tipo de código de respuesta
                val codigoEstado = response.code()
                when {
                    codigoEstado >= 500 -> mostrarErrorYRegresarInicio("Error del servidor")
                    codigoEstado >= 400 -> mostrarErrorYRegresarInicio("Error en la solicitud")
                    else -> mostrarErrorYRegresarInicio("Error de conexión")
                }
            }
        } catch (e: Exception) {
            Log.e("ProcesandoVideoActivity", "Excepción durante llamada API: ${e.message}")
            // Cancelar notificación de procesamiento
            NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
            isApiCallInProgress = false // Reset flag
            mostrarErrorYRegresarInicio("Error de conexión con el servidor")
        }
    }
    
    private suspend fun guardarArchivosEnCarpeta(data: RespuestaProcesamiento): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d("ProcesandoVideoActivity", "Iniciando guardado seguro de archivos...")
                
                // Crear gestor de almacenamiento
                val storageManager = VideoStorageManager(this@ProcesandoVideoActivity)
                
                val fecha = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val sessionName = "Sesion_$fecha"
                
                Log.d("ProcesandoVideoActivity", "Sesión: $sessionName")

                // Guardar video en carpeta privada con referencia en galería
                Log.d("ProcesandoVideoActivity", "Guardando video seguro...")
                val videoInfo = guardarArchivoSeguro(
                    storageManager,
                    ApiCliente.urlAbsoluta(data.video_url!!),
                    sessionName,
                    "Video_$fecha.mp4",
                    "video/mp4"
                )
                Log.d("ProcesandoVideoActivity", "Video guardado: ${videoInfo.privateFile.absolutePath}")
                if (videoInfo.galleryUri != null) {
                    Log.d("ProcesandoVideoActivity", "Video registrado en galería: ${videoInfo.galleryUri}")
                }

                // Guardar audio en carpeta privada
                Log.d("ProcesandoVideoActivity", "Guardando audio...")
                val audioInfo = guardarArchivoSeguro(
                    storageManager,
                    ApiCliente.urlAbsoluta(data.audio_url!!),
                    sessionName,
                    "audio_traducido.mp3",
                    "audio/mp3"
                )
                Log.d("ProcesandoVideoActivity", "Audio guardado: ${audioInfo.privateFile.absolutePath}")

                // Guardar texto en carpeta privada
                Log.d("ProcesandoVideoActivity", "Guardando texto...")
                val textoInfo = guardarArchivoSeguro(
                    storageManager,
                    ApiCliente.urlAbsoluta(data.texto_url!!),
                    sessionName,
                    "transcripcion.txt",
                    "text/plain"
                )
                Log.d("ProcesandoVideoActivity", "Texto guardado: ${textoInfo.privateFile.absolutePath}")

                // Verificar contenido del texto para detectar respuestas inválidas del servidor
                val contenidoTexto = textoInfo.privateFile.readText(Charsets.UTF_8).trim()
                Log.d("ProcesandoVideoActivity", "Contenido del texto: '$contenidoTexto'")
                
                // Detectar diferentes variantes de respuestas inválidas del servidor
                val esMalTraducido = contenidoTexto.isEmpty() || 
                                   contenidoTexto.equals("Sin detecciones válidas.", ignoreCase = true) ||
                                   contenidoTexto.equals("Sin detecciones validas.", ignoreCase = true) ||
                                   contenidoTexto.equals("Sin detecciones válidas", ignoreCase = true) ||
                                   contenidoTexto.equals("Sin detecciones validas", ignoreCase = true) ||
                                   contenidoTexto.contains("No se detectaron", ignoreCase = true) ||
                                   contenidoTexto.contains("Error en el procesamiento", ignoreCase = true)
                
                if (esMalTraducido) {
                    Log.w("ProcesandoVideoActivity", "DETECCIÓN AUTOMÁTICA: Texto contiene respuesta inválida: '$contenidoTexto'")
                    Log.w("ProcesandoVideoActivity", "VIDEO SERÁ MARCADO COMO MAL TRADUCIDO")
                    // Marcar como MAL TRADUCIDO (contenido inválido) - NO eliminar archivos
                    val videoPath = intent.getStringExtra("VIDEO_PATH")
                    videoPath?.let { path ->
                        val videoFile = File(path)
                        VideoTranslationStatusHelper.marcarVideoComoMalTraducido(videoFile)
                        Log.d("ProcesandoVideoActivity", "Video marcado como MAL TRADUCIDO (contenido: '$contenidoTexto')")
                    }
                } else {
                    Log.i("ProcesandoVideoActivity", "Contenido del texto es válido: Video correctamente traducido")
                }

                Log.d("ProcesandoVideoActivity", "Todos los archivos guardados exitosamente en carpeta privada")
                Log.i("ProcesandoVideoActivity", "Archivos seguros en: ${storageManager.getSessionPrivateDir(sessionName).absolutePath}")
                
                // Retornar si el video fue marcado como mal traducido
                return@withContext esMalTraducido
                
            } catch (e: Exception) {
                Log.e("ProcesandoVideoActivity", "Error en guardarArchivosEnCarpeta: ${e.message}")
                throw e
            }
        }
    
    /**
     * Guarda un archivo de forma segura usando el VideoStorageManager
     * Mantiene compatibilidad con la lógica existente
     */
    private suspend fun guardarArchivoSeguro(
        storageManager: VideoStorageManager,
        url: String,
        sessionName: String,
        fileName: String,
        mimeType: String
    ): VideoStorageManager.SavedFileInfo = withContext(Dispatchers.IO) {
        try {
            Log.d("ProcesandoVideoActivity", "Descargando $fileName desde: $url")
            
            val request = okhttp3.Request.Builder().url(url).build()
            val response = ApiCliente.httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Error al descargar $fileName: ${response.code}")
            }
            
            if (response.body == null) {
                Log.e("ProcesandoVideoActivity", "Respuesta vacía para $fileName")
                throw Exception("El servidor devolvió contenido vacío para $fileName")
            }

            // Usar el VideoStorageManager para guardar de forma segura
            val savedFileInfo = response.body?.byteStream()?.use { inputStream ->
                storageManager.saveFileWithGalleryReference(
                    inputStream = inputStream,
                    fileName = fileName,
                    mimeType = mimeType,
                    sessionFolder = sessionName
                )
            } ?: throw Exception("No se pudo obtener el contenido de $fileName")
            
            // Verificar que el archivo se guardó correctamente
            if (!savedFileInfo.privateFile.exists() || savedFileInfo.privateFile.length() == 0L) {
                throw Exception("El archivo $fileName se descargó pero está vacío o corrupto")
            }

            Log.d("ProcesandoVideoActivity", "Archivo guardado exitosamente: ${savedFileInfo.privateFile.absolutePath} (${savedFileInfo.privateFile.length()} bytes)")
            
            savedFileInfo
            
        } catch (e: Exception) {
            Log.e("ProcesandoVideoActivity", "Error al guardar $fileName: ${e.message}")
            throw e // Re-lanzar la excepción para que se maneje en el nivel superior
        }
    }
    
    private fun mostrarErrorYRegresarInicio(mensaje: String) {
        stopLoadingAnimation()

        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater
            val view = inflater.inflate(R.layout.dialog_error_mejorado, null)

            val txtMensaje = view.findViewById<TextView>(R.id.txtMensaje)
            val btnAceptar = view.findViewById<View>(R.id.btnAceptar)

            txtMensaje.text = mensaje

            val dialog = builder.setView(view).setCancelable(false).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnAceptar.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, InicioAppActivity::class.java)
                startActivity(intent)
                finish()
            }

            dialog.show()
        }
    }
    
    /**
     * Muestra un diálogo de error de conectividad más amigable con opción de reintentar
     */
    private fun mostrarErrorConectividad(estadoServidor: ServerConfig.ServerStatus, videoPath: String) {
        // Cancelar notificación de procesamiento
        NotificationManagerCompat.from(this@ProcesandoVideoActivity).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)
        stopLoadingAnimation()
        isApiCallInProgress = false // Reset flag para permitir reintentos
        
        runOnUiThread {
            val conectividadHelper = ConectividadHelper(this@ProcesandoVideoActivity)
            val (titulo, mensaje) = conectividadHelper.obtenerMensajeAmigable(estadoServidor)
            val permitirReintento = conectividadHelper.deberiaPermitirReintento(estadoServidor)
            
            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater
            val view = inflater.inflate(R.layout.dialog_error_mejorado, null)
            
            val iconoError = view.findViewById<ImageView>(R.id.iconoError)
            val txtTitulo = view.findViewById<TextView>(R.id.txtTitulo)
            val txtMensaje = view.findViewById<TextView>(R.id.txtMensaje)
            val txtSugerencia = view.findViewById<TextView>(R.id.txtSugerencia)
            val btnAceptar = view.findViewById<View>(R.id.btnAceptar)

            // Configurar contenido
            txtTitulo.text = titulo
            txtMensaje.text = mensaje
            txtSugerencia.text = when (estadoServidor) {
                ServerConfig.ServerStatus.SIN_CONEXION ->
                    "Activa tu Wi-Fi o datos móviles y vuelve a intentar"
                ServerConfig.ServerStatus.TIMEOUT ->
                    "Espera unos minutos y vuelve a intentar. El servidor puede estar congestionado"
                ServerConfig.ServerStatus.ERROR_SERVIDOR ->
                    "Nuestros técnicos están solucionando el problema. Regresa en unos minutos"
                ServerConfig.ServerStatus.NO_DISPONIBLE ->
                    "El servicio volverá pronto. Te recomendamos esperar un poco"
                else ->
                    "Si el problema persiste, contacta nuestro soporte técnico"
            }
            
            val dialog = builder.setView(view).setCancelable(false).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            
            // Animación del título
            DialogUtils.animarTituloColores(this@ProcesandoVideoActivity, txtTitulo)

            btnAceptar.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, InicioAppActivity::class.java)
                startActivity(intent)
                finish()
            }
            
            dialog.show()
        }
    }
    
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Bloquear COMPLETAMENTE el botón de retroceso durante cualquier tipo de procesamiento
        // No permitir regresar ni a la cámara ni al inicio para evitar romper la comunicación

        val isProcessingActive = isApiCallInProgress || isAnimating || currentSessionId != null

        if (!isProcessingActive) {
            // Solo permitir regresar si NO hay NINGÚN procesamiento activo
            super.onBackPressed()
        } else {
            // Mostrar mensaje claro al usuario indicando que debe esperar
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Procesamiento en curso. Por favor espera a que termine.",
                    Toast.LENGTH_LONG
                ).show()

                // Log para debugging
                Log.w("ProcesandoVideoActivity", "Intento de salir bloqueado - Procesamiento activo: API=$isApiCallInProgress, Animación=$isAnimating, WorkManager=${currentSessionId != null}")
            }
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        // Detener todas las animaciones para evitar memory leaks
        stopLoadingAnimation()
        
        // Reset flag en caso de que la actividad se destruya
        isApiCallInProgress = false
        
        // Si usamos WorkManager, el trabajo continúa en segundo plano
        // No cancelamos aquí para permitir que el procesamiento continúe
    }

    /**
     * Marca un video como mal traducido y muestra el diálogo de advertencia
     */
    private fun marcarVideoComoMalTraducidoYMostrarDialogo() {
        // Detener animación
        stopLoadingAnimation()

        // Marcar video como mal traducido
        val videoPath = intent.getStringExtra("VIDEO_PATH")
        videoPath?.let { path ->
            val videoFile = File(path)
            VideoTranslationStatusHelper.marcarVideoComoMalTraducido(videoFile)
            Log.d("ProcesandoVideoActivity", "Video marcado como mal traducido: ${videoFile.name}")
        }

        runOnUiThread {
            // Regresar al inicio después de un breve delay
            Handler(Looper.getMainLooper()).postDelayed({
                mostrarErrorYRegresarInicio("Video guardado pero requiere nueva traducción")
            }, 2000) // Tiempo para que el usuario lea el diálogo
        }
    }

    /**
     * Muestra UNA SOLA notificación final unificada con el logo de la app
     * Solo muestra notificación de éxito cuando el video es completamente normal
     * EJECUTA TODAS LAS OPERACIONES EN EL HILO PRINCIPAL
     */
    private fun mostrarNotificacionFinalUnificada(esExitoso: Boolean, mensajePersonalizado: String? = null) {
        runOnUiThread {
            val notificationHelper = NotificationHelper(this@ProcesandoVideoActivity)

            // SIEMPRE cancelar la notificación de procesamiento
            NotificationManagerCompat.from(this).cancel(NotificationHelper.NOTIFICATION_ID_PROCESSING)

            if (esExitoso) {
                // Solo mostrar notificación de éxito para videos completamente normales
                notificationHelper.mostrarNotificacionVideoExitoso()
                Log.d("ProcesandoVideoActivity", "Notificación final: ÉXITO - Video completamente normal")
            } else {
                // Para errores o videos mal traducidos, NO mostrar notificación adicional
                // Solo cancelar la de procesamiento
                Log.d("ProcesandoVideoActivity", "Notificación final: ERROR/MAL TRADUCIDO - Sin notificación adicional")
            }
        }
    }
}
