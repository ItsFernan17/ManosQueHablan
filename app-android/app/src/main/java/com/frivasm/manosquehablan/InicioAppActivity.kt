package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InicioAppActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_app)

        val btnNuevoVideo = findViewById<LinearLayout>(R.id.btnNuevoVideo)
        btnNuevoVideo.setOnClickListener {
            val intent = Intent(this, GrabarVideoActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            cargarVideosGrabados()
        } catch (e: Exception) {
            Log.e("InicioApp", "Error al recargar videos: ${e.message}")
        }
    }

    private fun cargarVideosGrabados() {
        val directorio = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ManosQueHablan"
        )

        if (!directorio.exists()) {
            Log.w("InicioApp", "Directorio no existe: ${directorio.absolutePath}")
            return
        }

        val listaVideos = directorio.listFiles()?.filter { file ->
            file.isFile &&
                    file.extension.lowercase(Locale.getDefault()) == "mp4" &&
                    !file.name.startsWith(".") &&
                    file.length() > 1_000_000
        } ?: emptyList()

        val listaVideosSinDuplicados = listaVideos.distinctBy { it.name }
        val videosOrdenados = listaVideosSinDuplicados.sortedByDescending { it.lastModified() }

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val hoy = formatoFecha.format(Date())

        val contenedor = findViewById<LinearLayout>(R.id.contenedorVideos)
        val vistaSinVideos = findViewById<LinearLayout>(R.id.vistaSinVideos)
        val inflater = LayoutInflater.from(this)

        contenedor.removeAllViews()

        if (videosOrdenados.isEmpty()) {
            vistaSinVideos.visibility = View.VISIBLE
            return
        }

        val videosHoy = videosOrdenados.filter {
            val fecha = formatoFecha.format(Date(it.lastModified()))
            fecha == hoy
        }

        if (videosHoy.isEmpty()) {
            vistaSinVideos.visibility = View.GONE
        }

        // Mostrar destacado
        if (videosHoy.isNotEmpty()) {
            val videoDestacado = videosHoy.first()
            val vistaDestacada = inflater.inflate(R.layout.item_video_destacado, contenedor, false)
            cargarDatosEnVista(vistaDestacada, videoDestacado, formatoFecha)
            contenedor.addView(vistaDestacada)

            for (video in videosHoy.drop(1)) {
                val vista = inflater.inflate(R.layout.item_video_normal, contenedor, false)
                cargarDatosEnVista(vista, video, formatoFecha)
                contenedor.addView(vista)
            }
        }

        // Mostrar videos anteriores
        val videosAnteriores = videosOrdenados.filter {
            val fecha = formatoFecha.format(Date(it.lastModified()))
            fecha != hoy
        }

        for (video in videosAnteriores) {
            val vista = inflater.inflate(R.layout.item_video_normal, contenedor, false)
            cargarDatosEnVista(vista, video, formatoFecha)
            contenedor.addView(vista)
        }

        vistaSinVideos.visibility = View.GONE
    }

    private fun cargarDatosEnVista(vista: View, video: File, formatoFecha: SimpleDateFormat) {
        val titulo = vista.findViewById<TextView>(R.id.txtTitulo)
        val fechaTxt = vista.findViewById<TextView>(R.id.txtFecha)
        val miniatura = vista.findViewById<ImageView>(R.id.imgMiniatura)

        val nombreLimpio = video.nameWithoutExtension.replace("_", " ").replace("-", " ")
        titulo.text = nombreLimpio
        fechaTxt.text = formatoFecha.format(Date(video.lastModified()))

        Glide.with(this)
            .load(video.absolutePath)
            .thumbnail(0.1f)
            .into(miniatura)
    }
}
