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
                    PreferenciasHelper.guardarOrden(this, tipo)
                    aplicarOrdenamiento(tipo)
                }
            )
        }

        aplicarOrdenamiento(PreferenciasHelper.obtenerOrden(this))
    }

    private fun animateButtonAndNavigate() {
        // Crear animación de escala y rebote
        val scaleDown = ObjectAnimator.ofFloat(btnNuevoVideo, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(btnNuevoVideo, "scaleY", 1f, 0.95f)
        
        val scaleUp = ObjectAnimator.ofFloat(btnNuevoVideo, "scaleX", 0.95f, 1.05f)
        val scaleUpY = ObjectAnimator.ofFloat(btnNuevoVideo, "scaleY", 0.95f, 1.05f)
        
        val scaleNormal = ObjectAnimator.ofFloat(btnNuevoVideo, "scaleX", 1.05f, 1f)
        val scaleNormalY = ObjectAnimator.ofFloat(btnNuevoVideo, "scaleY", 1.05f, 1f)
        
        // Secuencia de animación: bajar → subir → normal
        val animatorSet = AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY)
            duration = 100
        }
        
        val animatorSet2 = AnimatorSet().apply {
            playTogether(scaleUp, scaleUpY)
            duration = 150
        }
        
        val animatorSet3 = AnimatorSet().apply {
            playTogether(scaleNormal, scaleNormalY)
            duration = 100
        }
        
        // Secuencia completa
        val fullAnimation = AnimatorSet().apply {
            playSequentially(animatorSet, animatorSet2, animatorSet3)
        }
        
        // Ejecutar animación y navegar al final
        fullAnimation.start()
        
        // Navegar después de la animación
        btnNuevoVideo.postDelayed({
            startActivity(Intent(this, GrabarVideoActivity::class.java))
        }, 350) // 350ms para que termine la animación
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
            "recientes" -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
            "fecha"     -> VideoOrdenamientoViewHelper.ordenarVideosPorFecha(this, contenedor, vistaSinVideos)
            "alfabetico"-> VideoOrdenamientoAlfabeticoViewHelper.ordenarVideosPorLetra(this, contenedor, vistaSinVideos)
            else        -> VideoLoader.cargarVideosRecientes(this, contenedor, vistaSinVideos)
        }
    }
}
