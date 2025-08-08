package com.frivasm.manosquehablan

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_app)

        contenedor = findViewById(R.id.contenedorVideos)
        vistaSinVideos = findViewById(R.id.vistaSinVideos)
        btnOpciones = findViewById(R.id.btnOpciones)

        findViewById<LinearLayout>(R.id.btnNuevoVideo).setOnClickListener {
            startActivity(Intent(this, GrabarVideoActivity::class.java))
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
