package com.frivasm.manosquehablan

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.frivasm.manosquehablan.helpers.VideoLoader
import com.frivasm.manosquehablan.helpers.VideoOrdenamientoViewHelper
import com.frivasm.manosquehablan.helpers.PreferenciasHelper
import com.frivasm.manosquehablan.helpers.OrdenamientoBottomSheetHelper
import com.frivasm.manosquehablan.dialogs.DialogUtils
import com.frivasm.manosquehablan.utils.VideoUtils
import com.frivasm.manosquehablan.GrabarVideoActivity
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.helpers.VideoOrdenamientoAlfabeticoViewHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InicioAppActivity : AppCompatActivity() {

    companion object {
        private const val ORDEN_RECIENTES = "recientes"
        private const val ORDEN_FECHA = "fecha"
        private const val ORDEN_ALFABETICO = "alfabetico"
        private const val ANIMATION_TRANSITION_DURATION = 200L // Animación para cambios de vista
    }

    private lateinit var contenedor: LinearLayout
    private lateinit var vistaSinVideos: LinearLayout
    private lateinit var btnOpciones: ImageView
    private lateinit var btnNuevoVideo: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_app)

        contenedor = findViewById(R.id.contenedorVideos)
        vistaSinVideos = findViewById(R.id.vistaSinVideos)
        btnOpciones = findViewById(R.id.btnOpciones)
        btnNuevoVideo = findViewById(R.id.btnNuevoVideo)

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

        aplicarOrdenamientoConAnimacion(PreferenciasHelper.obtenerOrden(this), conTransicion = false)
    }

    private fun animateButtonAndNavigate() {
        btnNuevoVideo.isEnabled = false // Evitar doble click

        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            btnNuevoVideo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 0.95f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 0.95f)
        ).apply { duration = 100 }

        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            btnNuevoVideo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.05f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.05f)
        ).apply { duration = 150 }

        val scaleNormal = ObjectAnimator.ofPropertyValuesHolder(
            btnNuevoVideo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f)
        ).apply { duration = 100 }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, scaleNormal)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    startActivity(Intent(this@InicioAppActivity, GrabarVideoActivity::class.java))
                    btnNuevoVideo.isEnabled = true // Reactivar al volver
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
        val cargarVideos = {
            when (tipo) {
                ORDEN_RECIENTES -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
                ORDEN_FECHA -> VideoOrdenamientoViewHelper.ordenarVideosPorFecha(this, contenedor, vistaSinVideos)
                ORDEN_ALFABETICO -> VideoOrdenamientoAlfabeticoViewHelper.ordenarVideosPorLetra(this, contenedor, vistaSinVideos)
                else -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
            }
            // Asegurar layout y redraw
            contenedor.requestLayout()
            contenedor.invalidate()
        }

        if (conTransicion && contenedor.childCount > 0) {
            // Animación rápida de transición para cambios de vista
            val slideOut = ObjectAnimator.ofFloat(contenedor, View.TRANSLATION_X, 0f, -30f)
            val fadeOut = ObjectAnimator.ofFloat(contenedor, View.ALPHA, 1f, 0.4f)
            
            val animSetOut = AnimatorSet()
            animSetOut.playTogether(slideOut, fadeOut)
            animSetOut.duration = ANIMATION_TRANSITION_DURATION
            
            animSetOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cargarVideos()
                    
                    // Slide in desde la derecha + fade in
                    contenedor.translationX = 30f
                    contenedor.alpha = 0.4f
                    
                    val slideIn = ObjectAnimator.ofFloat(contenedor, View.TRANSLATION_X, 30f, 0f)
                    val fadeIn = ObjectAnimator.ofFloat(contenedor, View.ALPHA, 0.4f, 1f)
                    
                    val animSetIn = AnimatorSet()
                    animSetIn.playTogether(slideIn, fadeIn)
                    animSetIn.duration = ANIMATION_TRANSITION_DURATION
                    animSetIn.start()
                }
            })
            animSetOut.start()
        } else {
            // Sin animación
            cargarVideos()
            // Asegurar que el contenedor esté en posición normal
            contenedor.alpha = 1f
            contenedor.translationX = 0f
        }
    }

    /**
     * Elimina un video específico con animación sin recargar toda la lista
     */
    fun eliminarVideoConAnimacion(vista: View, videoFile: File) {
        // Verificar que la vista esté en el contenedor
        if (vista.parent == contenedor) {
            // Crear animación de desvanecimiento para la vista específica
            val scaleX = ObjectAnimator.ofFloat(vista, View.SCALE_X, 1f, 0f)
            val scaleY = ObjectAnimator.ofFloat(vista, View.SCALE_Y, 1f, 0f)
            val alpha = ObjectAnimator.ofFloat(vista, View.ALPHA, 1f, 0f)
            
            val animSet = AnimatorSet()
            animSet.playTogether(scaleX, scaleY, alpha)
            animSet.duration = 400L // Duración para eliminación
            
            animSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Eliminar la vista del contenedor cuando termine la animación
                    contenedor.removeView(vista)
                    
                    // Si no quedan más videos, mostrar la vista sin videos
                    if (contenedor.childCount == 0) {
                        vistaSinVideos.visibility = View.VISIBLE
                    }
                }
            })
            
            animSet.start()
        } else {
            // Si la vista no está en el contenedor, recargar normalmente sin animación
            aplicarOrdenamientoConAnimacion(PreferenciasHelper.obtenerOrden(this), conTransicion = false)
        }
    }
}
