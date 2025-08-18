package com.frivasm.manosquehablan

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

        aplicarOrdenamiento(PreferenciasHelper.obtenerOrden(this))
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
            aplicarOrdenamiento(PreferenciasHelper.obtenerOrden(this))
        } catch (e: Exception) {
            Log.e("InicioApp", "Error al recargar videos: ${e.message}")
        }
    }

    private fun aplicarOrdenamiento(tipo: String) {
        when (tipo) {
            ORDEN_RECIENTES -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
            ORDEN_FECHA -> VideoOrdenamientoViewHelper.ordenarVideosPorFecha(this, contenedor, vistaSinVideos)
            ORDEN_ALFABETICO -> VideoOrdenamientoAlfabeticoViewHelper.ordenarVideosPorLetra(this, contenedor, vistaSinVideos)
            else -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
        }
    }
}
