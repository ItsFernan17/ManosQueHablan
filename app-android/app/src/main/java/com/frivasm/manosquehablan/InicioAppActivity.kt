package com.frivasm.manosquehablan

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.lifecycle.lifecycleScope
import com.frivasm.manosquehablan.helpers.VideoLoader
import com.frivasm.manosquehablan.helpers.VideoOrdenamientoViewHelper
import com.frivasm.manosquehablan.helpers.PreferenciasHelper
import com.frivasm.manosquehablan.helpers.OrdenamientoBottomSheetHelper
import com.frivasm.manosquehablan.GrabarVideoActivity
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.helpers.VideoOrdenamientoAlfabeticoViewHelper
import com.frivasm.manosquehablan.helpers.VideoViewBuilder
import com.frivasm.manosquehablan.helpers.VideoSyncCache
import com.frivasm.manosquehablan.dialogs.DialogUtils
import com.frivasm.manosquehablan.config.ServerConfig
import java.io.File
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.util.*

@ExperimentalCamera2Interop
class InicioAppActivity : AppCompatActivity() {

    companion object {
        private const val ORDEN_RECIENTES = "recientes"
        private const val ORDEN_FECHA = "fecha"
        private const val ORDEN_ALFABETICO = "alfabetico"
        private const val ANIMATION_TRANSITION_DURATION = 350L // Aumentado para más suavidad
        private const val ANIMATION_ELIMINATION_DURATION = 320L // Reducido para eliminación más rápida
        private const val ANIMATION_PROMOTION_DURATION = 300L // Para promociones
    }

    private lateinit var contenedor: LinearLayout
    private lateinit var vistaSinVideos: LinearLayout
    private lateinit var btnOpciones: ImageView
    private lateinit var btnInfo: ImageView
    private lateinit var btnCatalogo: ImageView
    private lateinit var btnBuscar: ImageView
    private lateinit var btnNuevoVideo: LinearLayout
    
    // Variables para gestión de animaciones
    private var currentTransitionAnimator: AnimatorSet? = null
    private var currentEliminationAnimator: AnimatorSet? = null
    private var currentPromotionAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_app)

        // Log de configuración de servidor
        Log.i("ServerConfig", ServerConfig.getServerInfo())

        contenedor = findViewById(R.id.contenedorVideos)
        vistaSinVideos = findViewById(R.id.vistaSinVideos)
        btnOpciones = findViewById(R.id.btnOpciones)
        btnInfo = findViewById(R.id.btnInfo)
        btnCatalogo = findViewById(R.id.btnCatalogo)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnNuevoVideo = findViewById(R.id.btnNuevoVideo)

        // Verificar si debe mostrar diálogo de mal traducido
        val mostrarDialogoMalTraducido = intent.getBooleanExtra("MOSTRAR_DIALOGO_MAL_TRADUCIDO", false)
        if (mostrarDialogoMalTraducido) {
            // Mostrar el diálogo después de que la interfaz esté lista
            findViewById<View>(android.R.id.content).post {
                DialogUtils.mostrarDialogoVideoMalTraducido(this)
            }
        }

        btnNuevoVideo.setOnClickListener {
            // Animar el botón antes de navegar
            animateButtonAndNavigate()
        }

        btnOpciones.setOnClickListener {
            OrdenamientoBottomSheetHelper.mostrarBottomSheetOrdenamiento(
                context = this,
                onTipoSeleccionado = { tipo ->
                    // El helper ya guarda la preferencia, solo necesitamos reaccionar.
                    aplicarOrdenamiento(tipo)
                }
            )
        }

        btnInfo.setOnClickListener {
            DialogUtils.mostrarDialogoAcercaDe(this)
        }

        btnCatalogo.setOnClickListener {
            val intent = Intent(this, CatalogoSeniasActivity::class.java)
            startActivity(intent)
        }

        btnBuscar.setOnClickListener {
            val intent = Intent(this, SearchVideoActivity::class.java)
            startActivity(intent)
        }

        aplicarOrdenamientoConAnimacion(PreferenciasHelper.obtenerOrden(this), conTransicion = false)
        
        // Verificar si es la primera vez y mostrar diálogo de bienvenida
        if (PreferenciasHelper.esPrimeraVez(this)) {
            // Usar un pequeño delay para que la actividad termine de cargar
            btnNuevoVideo.postDelayed({
                DialogUtils.mostrarDialogoBienvenida(this)
            }, 500)
        }
        
        // PARA PRUEBAS: Descomentar la siguiente línea para resetear y ver el diálogo nuevamente
        // PreferenciasHelper.marcarBienvenidaMostrada(this) // comentar esta línea para resetear
    }

    private fun animateButtonAndNavigate() {
        btnNuevoVideo.isEnabled = false // Evitar doble click

        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            btnNuevoVideo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.92f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.92f)
        ).apply { 
            duration = 120
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            btnNuevoVideo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.08f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.08f)
        ).apply { 
            duration = 180
            interpolator = DecelerateInterpolator()
        }

        val scaleNormal = ObjectAnimator.ofPropertyValuesHolder(
            btnNuevoVideo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f)
        ).apply { 
            duration = 120
            interpolator = OvershootInterpolator(0.5f)
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, scaleNormal)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Verificar si debe mostrar el recordatorio
                    if (PreferenciasHelper.deberMostrarRecordatorioGrabacion(this@InicioAppActivity)) {
                        DialogUtils.mostrarDialogoRecordatorioGrabar(this@InicioAppActivity) {
                            // Continuar a la cámara después del diálogo
                            startActivity(Intent(this@InicioAppActivity, GrabarVideoActivity::class.java))
                        }
                    } else {
                        // Ir directamente a la cámara
                        startActivity(Intent(this@InicioAppActivity, GrabarVideoActivity::class.java))
                    }
                    btnNuevoVideo.isEnabled = true // Reactivar al volver
                    
                    // PARA PRUEBAS: Descomentar para resetear y volver a ver el diálogo
                    // SharedPreferences prefs = getSharedPreferences("preferencias", MODE_PRIVATE);
                    // prefs.edit().remove("recordatorio_grabacion_deshabilitado").apply();
                }
            })
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            aplicarOrdenamientoConAnimacion(PreferenciasHelper.obtenerOrden(this), conTransicion = false)
        } catch (e: Exception) {
            Log.e("InicioApp", "Error al recargar videos: ${e.message}")
        }
    }

    fun aplicarOrdenamiento(tipo: String) {
        aplicarOrdenamientoConAnimacion(tipo, conTransicion = true)
    }

    private fun aplicarOrdenamientoConAnimacion(tipo: String, conTransicion: Boolean = false) {
        // Cancelar animación de transición previa si existe
        currentTransitionAnimator?.cancel()
        currentTransitionAnimator = null
        
        val cargarVideos = {
            when (tipo) {
                ORDEN_RECIENTES -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
                ORDEN_FECHA -> VideoOrdenamientoViewHelper.ordenarVideosPorFecha(this, contenedor, vistaSinVideos, lifecycleScope)
                ORDEN_ALFABETICO -> VideoOrdenamientoAlfabeticoViewHelper.ordenarVideosPorLetra(this, contenedor, vistaSinVideos, lifecycleScope)
                else -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
            }
            // Asegurar layout y redraw
            contenedor.requestLayout()
            contenedor.invalidate()
        }

        if (conTransicion && contenedor.childCount > 0) {
            // Animación minimalista y elegante de transición
            val fadeOut = ObjectAnimator.ofFloat(contenedor, View.ALPHA, 1f, 0.4f)
            val scaleOut = ObjectAnimator.ofFloat(contenedor, View.SCALE_X, 1f, 0.96f)
            val scaleOutY = ObjectAnimator.ofFloat(contenedor, View.SCALE_Y, 1f, 0.96f)
            
            currentTransitionAnimator = AnimatorSet()
            currentTransitionAnimator!!.playTogether(fadeOut, scaleOut, scaleOutY)
            currentTransitionAnimator!!.duration = ANIMATION_TRANSITION_DURATION
            currentTransitionAnimator!!.interpolator = DecelerateInterpolator(1.5f)
            
            currentTransitionAnimator!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cargarVideos()
                    
                    // Posición inicial para entrada sutil
                    contenedor.alpha = 0.4f
                    contenedor.scaleX = 0.96f
                    contenedor.scaleY = 0.96f
                    
                    // Entrada suave y minimalista
                    val fadeIn = ObjectAnimator.ofFloat(contenedor, View.ALPHA, 0.4f, 1f)
                    val scaleInX = ObjectAnimator.ofFloat(contenedor, View.SCALE_X, 0.96f, 1f)
                    val scaleInY = ObjectAnimator.ofFloat(contenedor, View.SCALE_Y, 0.96f, 1f)
                    
                    val animSetIn = AnimatorSet()
                    animSetIn.playTogether(fadeIn, scaleInX, scaleInY)
                    animSetIn.duration = ANIMATION_TRANSITION_DURATION
                    animSetIn.interpolator = OvershootInterpolator(0.6f)
                    animSetIn.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            currentTransitionAnimator = null
                        }
                    })
                    animSetIn.start()
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    currentTransitionAnimator = null
                }
            })
            currentTransitionAnimator!!.start()
        } else {
            // Sin animación
            cargarVideos()
            // Asegurar que el contenedor esté en posición normal
            contenedor.alpha = 1f
            contenedor.scaleX = 1f
            contenedor.scaleY = 1f
        }
    }

    /**
     * Elimina un video específico con animación sin recargar toda la lista
     */
    fun eliminarVideoConAnimacion(vista: View, videoFile: File) {
        // Verificar que la vista esté en el contenedor
        if (vista.parent == contenedor) {
            val esPrimerVideo = contenedor.indexOfChild(vista) == 0
            val ordenActual = PreferenciasHelper.obtenerOrden(this)
            
            // Animación minimalista y limpia de eliminación
            val scaleX = ObjectAnimator.ofFloat(vista, View.SCALE_X, 1f, 0.85f)
            val scaleY = ObjectAnimator.ofFloat(vista, View.SCALE_Y, 1f, 0.85f)
            val alpha = ObjectAnimator.ofFloat(vista, View.ALPHA, 1f, 0f)
            val translationY = ObjectAnimator.ofFloat(vista, View.TRANSLATION_Y, 0f, -20f)
            
            currentEliminationAnimator = AnimatorSet()
            currentEliminationAnimator!!.playTogether(scaleX, scaleY, alpha, translationY)
            currentEliminationAnimator!!.duration = ANIMATION_ELIMINATION_DURATION
            currentEliminationAnimator!!.interpolator = DecelerateInterpolator(1.8f)
            
            currentEliminationAnimator!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Eliminar la vista del contenedor cuando termine la animación
                    contenedor.removeView(vista)
                    
                    // Si no quedan más videos, mostrar la vista sin videos
                    if (contenedor.childCount == 0) {
                        vistaSinVideos.visibility = View.VISIBLE
                    } else if (esPrimerVideo && contenedor.childCount > 0 && ordenActual == ORDEN_RECIENTES) {
                        // Si eliminamos el video destacado y estamos en modo "recientes", promover el siguiente
                        promoverSiguienteVideoADestacado()
                    }
                    currentEliminationAnimator = null
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    currentEliminationAnimator = null
                }
            })
            
            currentEliminationAnimator!!.start()
        } else {
            // Si la vista no está en el contenedor, recargar normalmente sin animación
            aplicarOrdenamientoConAnimacion(PreferenciasHelper.obtenerOrden(this), conTransicion = false)
        }
    }

    /**
     * Convierte el primer video de la lista a layout destacado de forma suave
     * Solo funciona para el ordenamiento por "recientes"
     */
    private fun promoverSiguienteVideoADestacado() {
        if (contenedor.childCount == 0) return
        
        val siguienteVista = contenedor.getChildAt(0)
        val videoFile = siguienteVista.tag as? File ?: return
        
        // Verificar que el archivo aún existe antes de proceder
        if (!videoFile.exists()) {
            // Si el archivo no existe, recargar la vista completa
            aplicarOrdenamientoConAnimacion(PreferenciasHelper.obtenerOrden(this), conTransicion = false)
            return
        }
        
        // Obtener datos necesarios para recrear la vista
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val inflater = LayoutInflater.from(this)
        
        // Crear nueva vista con layout destacado
        val nuevaVistaDestacada = inflater.inflate(R.layout.item_video_destacado, contenedor, false)
        
        // Configurar la nueva vista destacada
        VideoViewBuilder.construirVistaVideoNormal(this, nuevaVistaDestacada, videoFile, formatoFecha) { nuevoArchivo ->
            // Callback para actualizaciones del video
            nuevaVistaDestacada.tag = nuevoArchivo
            val nuevoTitulo = nuevaVistaDestacada.findViewById<TextView>(R.id.txtTitulo)
            nuevoTitulo.text = nuevoArchivo.nameWithoutExtension.replace("_", " ").replace("-", " ")
            VideoSyncCache.videosActualizados[videoFile.absolutePath] = nuevoArchivo
        }
        
        // Preparar animación elegante de promoción
        nuevaVistaDestacada.alpha = 0f
        nuevaVistaDestacada.scaleX = 0.7f
        nuevaVistaDestacada.scaleY = 0.7f
        nuevaVistaDestacada.translationY = -30f
        nuevaVistaDestacada.rotationX = -15f
        
        // Remover la vista anterior y agregar la nueva
        contenedor.removeViewAt(0)
        contenedor.addView(nuevaVistaDestacada, 0)
        
        // Animar la entrada elegante de la nueva vista destacada
        val fadeIn = ObjectAnimator.ofFloat(nuevaVistaDestacada, View.ALPHA, 0f, 1f)
        val scaleXIn = ObjectAnimator.ofFloat(nuevaVistaDestacada, View.SCALE_X, 0.7f, 1f)
        val scaleYIn = ObjectAnimator.ofFloat(nuevaVistaDestacada, View.SCALE_Y, 0.7f, 1f)
        val translateYIn = ObjectAnimator.ofFloat(nuevaVistaDestacada, View.TRANSLATION_Y, -30f, 0f)
        val rotationXIn = ObjectAnimator.ofFloat(nuevaVistaDestacada, View.ROTATION_X, -15f, 0f)
        
        currentPromotionAnimator = AnimatorSet()
        currentPromotionAnimator!!.playTogether(fadeIn, scaleXIn, scaleYIn, translateYIn, rotationXIn)
        currentPromotionAnimator!!.duration = ANIMATION_PROMOTION_DURATION
        currentPromotionAnimator!!.interpolator = OvershootInterpolator(0.8f)
        currentPromotionAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                currentPromotionAnimator = null
            }
            
            override fun onAnimationCancel(animation: Animator) {
                currentPromotionAnimator = null
            }
        })
        currentPromotionAnimator!!.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar todas las animaciones activas para evitar memory leaks
        currentTransitionAnimator?.cancel()
        currentEliminationAnimator?.cancel()
        currentPromotionAnimator?.cancel()
        currentTransitionAnimator = null
        currentEliminationAnimator = null
        currentPromotionAnimator = null
    }
}
