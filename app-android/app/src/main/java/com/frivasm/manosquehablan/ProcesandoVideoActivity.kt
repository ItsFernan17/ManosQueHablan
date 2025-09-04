package com.frivasm.manosquehablan

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.media.RingtoneManager
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
import androidx.lifecycle.lifecycleScope
import com.frivasm.manosquehablan.api.ApiCliente
import com.frivasm.manosquehablan.api.RespuestaProcesamiento
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
    
    // Variable para controlar que la API solo se llame una vez
    private var isApiCallInProgress = false
    
    // Referencias a animadores para poder cancelarlos
    private val activeAnimators = mutableListOf<ObjectAnimator>()
    private val activeValueAnimators = mutableListOf<ValueAnimator>()
    private val activeAnimatorSets = mutableListOf<AnimatorSet>()
    private val activeHandlers = mutableListOf<Handler>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_procesando_video)
        
        // Inicializar vistas
        initializeViews()
        
        // Iniciar animación de loading
        startLoadingAnimation()
        
        // Obtener el path del video desde el intent
        val videoPath = intent.getStringExtra("VIDEO_PATH")
        if (videoPath != null) {
            // Solo usar fondo negro sin miniatura del video
            enviarVideoAPI(videoPath)
        } else {
            mostrarErrorYRegresarInicio("Error: No se encontró el video a procesar")
        }
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
    }

    private fun startLoadingAnimation() {
        isAnimating = true
        
        // Iniciar animaciones de los círculos de fondo
        startCirclesAnimation()
        
        // Iniciar animación del círculo de progreso
        startProgressCircleAnimation()
        
        // Iniciar animación de los puntos de carga
        startLoadingDotsAnimation()
    }
    
    private fun stopLoadingAnimation() {
        isAnimating = false
        
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
    

    
    private fun enviarVideoAPI(path: String) {
        // Verificar que no haya otra llamada en progreso
        if (isApiCallInProgress) {
            Log.d("ProcesandoVideoActivity", "Llamada al API ya en progreso, ignorando...")
            return
        }
        
        isApiCallInProgress = true
        Log.d("ProcesandoVideoActivity", "Iniciando llamada al API...")
        
        val videoFile = File(path)
        val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiCliente.instance.procesarVideo(body)
                }
                
                Log.d("ProcesandoVideoActivity", "Respuesta del API: ${response.code()} - ${response.message()}")
                
                if (response.isSuccessful && response.body() != null) {
                    Log.d("ProcesandoVideoActivity", "API exitoso, validando respuesta...")
                    val data = response.body()!!
                    
                    // Validar que todos los archivos necesarios estén disponibles
                    if (data.video_url.isNullOrBlank()) {
                        Log.e("ProcesandoVideoActivity", "El servidor no proporcionó URL del video")
                        mostrarErrorYRegresarInicio("Error: el servidor no generó el video")
                        return@launch
                    }
                    
                    if (data.audio_url.isNullOrBlank()) {
                        Log.e("ProcesandoVideoActivity", "El servidor no proporcionó URL del audio")
                        mostrarErrorYRegresarInicio("Error: el servidor no generó el audio")
                        return@launch
                    }
                    
                    if (data.texto_url.isNullOrBlank()) {
                        Log.e("ProcesandoVideoActivity", "El servidor no proporcionó URL del texto")
                        mostrarErrorYRegresarInicio("Error: el servidor no generó la transcripción")
                        return@launch
                    }
                    
                    try {
                        Log.d("ProcesandoVideoActivity", "Todos los archivos disponibles, guardando...")
                        guardarArchivosEnCarpeta(data)
                        
                        // Detener animación
                        stopLoadingAnimation()
                        
                        // ✅ REPRODUCIR SONIDO SUAVE DE CONFIRMACIÓN
                        reproducirSonidoConfirmacion()
                        
                        // Mostrar toast de éxito y navegar con delay más corto
                        Toast.makeText(
                            this@ProcesandoVideoActivity,
                            "Video guardado correctamente",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Delay optimizado para que el usuario vea el toast pero no espere demasiado
                        delay(800)
                        val intent = Intent(this@ProcesandoVideoActivity, InicioAppActivity::class.java)
                        startActivity(intent)
                        isApiCallInProgress = false // Reset flag
                        finish()
                        
                    } catch (e: Exception) {
                        Log.e("ProcesandoVideoActivity", "Error al guardar archivos: ${e.message}")
                        isApiCallInProgress = false // Reset flag
                        mostrarErrorYRegresarInicio("Error al descargar los archivos del servidor")
                    }
                } else {
                    Log.e("ProcesandoVideoActivity", "API no exitoso: ${response.code()} - ${response.message()}")
                    
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
                isApiCallInProgress = false // Reset flag
                mostrarErrorYRegresarInicio("Error de conexión con el servidor")
            }
        }
    }
    
    private suspend fun guardarArchivosEnCarpeta(data: RespuestaProcesamiento) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("ProcesandoVideoActivity", "Iniciando guardado de archivos...")
                val fecha = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val nombreCarpeta = "ManosQueHablan/Sesion_$fecha"
                val carpeta = File(this@ProcesandoVideoActivity.getExternalFilesDir(null), nombreCarpeta)
                if (!carpeta.exists()) carpeta.mkdirs()
                
                Log.d("ProcesandoVideoActivity", "Carpeta creada: ${carpeta.absolutePath}")

                // Guardar video con nombre personalizado
                Log.d("ProcesandoVideoActivity", "Guardando video...")
                guardarArchivoLocal(
                    ApiCliente.urlAbsoluta(data.video_url!!),
                    carpeta,
                    "Video_$fecha.mp4",
                    "video/mp4"
                )

                // Guardar audio
                Log.d("ProcesandoVideoActivity", "Guardando audio...")
                guardarArchivoLocal(
                    ApiCliente.urlAbsoluta(data.audio_url!!),
                    carpeta,
                    "audio_traducido.mp3",
                    "audio/mp3"
                )

                // Guardar texto
                Log.d("ProcesandoVideoActivity", "Guardando texto...")
                guardarArchivoLocal(
                    ApiCliente.urlAbsoluta(data.texto_url!!),
                    carpeta,
                    "transcripcion.txt",
                    "text/plain"
                )

                Log.d("ProcesandoVideoActivity", "Todos los archivos guardados exitosamente")
            } catch (e: Exception) {
                Log.e("ProcesandoVideoActivity", "Error en guardarArchivosEnCarpeta: ${e.message}")
                throw e
            }
        }
    
    private suspend fun guardarArchivoLocal(
        url: String,
        carpetaDestino: File,
        nombreArchivo: String,
        mimeType: String
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d("ProcesandoVideoActivity", "Descargando $nombreArchivo desde: $url")
            
            val request = okhttp3.Request.Builder().url(url).build()
            val response = ApiCliente.httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Error al descargar $nombreArchivo: ${response.code}")
            }
            
            if (response.body == null) {
                Log.e("ProcesandoVideoActivity", "Respuesta vacía para $nombreArchivo")
                throw Exception("El servidor devolvió contenido vacío para $nombreArchivo")
            }

            val archivoDestino = File(carpetaDestino, nombreArchivo)
            response.body?.byteStream()?.use { input ->
                archivoDestino.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("No se pudo obtener el contenido de $nombreArchivo")
            
            // Verificar que el archivo se descargó correctamente
            if (!archivoDestino.exists() || archivoDestino.length() == 0L) {
                throw Exception("El archivo $nombreArchivo se descargó pero está vacío o corrupto")
            }

            MediaScannerConnection.scanFile(
                this@ProcesandoVideoActivity,
                arrayOf(archivoDestino.absolutePath),
                arrayOf(mimeType),
                null
            )

            Log.d("ProcesandoVideoActivity", "Archivo guardado exitosamente: ${archivoDestino.absolutePath} (${archivoDestino.length()} bytes)")
        } catch (e: Exception) {
            Log.e("ProcesandoVideoActivity", "Error al guardar $nombreArchivo: ${e.message}")
            throw e // Re-lanzar la excepción para que se maneje en el nivel superior
        }
    }
    
    private fun mostrarErrorYRegresarInicio(mensaje: String) {
        stopLoadingAnimation()
        
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater
            val view = inflater.inflate(R.layout.dialog_error, null)
            
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
    
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Bloquear el botón de retroceso durante el procesamiento
        // Llamamos super pero no hacemos nada más
        super.onBackPressed()
    }
    
    /**
     * Reproduce un sonido suave y agradable para confirmar que el video se guardó exitosamente
     * Especialmente útil para personas sin problemas auditivos como complemento a las señas
     */
    private fun reproducirSonidoConfirmacion() {
        try {
            Log.d("ProcesandoVideoActivity", "🔊 Reproduciendo sonido de confirmación...")
            
            val toneG = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, toneG)
            r.play()
            
            Log.d("ProcesandoVideoActivity", "✅ Sonido de confirmación reproducido exitosamente")
            
        } catch (e: Exception) {
            Log.w("ProcesandoVideoActivity", "❌ Error reproduciendo sonido de confirmación: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Detener todas las animaciones para evitar memory leaks
        stopLoadingAnimation()
        
        // Reset flag en caso de que la actividad se destruya
        isApiCallInProgress = false
    }
}
