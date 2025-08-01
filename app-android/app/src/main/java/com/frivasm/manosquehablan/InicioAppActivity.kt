package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import android.media.MediaScannerConnection
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InicioAppActivity : AppCompatActivity() {

    private lateinit var contenedor: LinearLayout
    private lateinit var vistaSinVideos: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_app)

        contenedor = findViewById(R.id.contenedorVideos)
        vistaSinVideos = findViewById(R.id.vistaSinVideos)

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
        val raiz = File(getExternalFilesDir(null), "ManosQueHablan")
        if (!raiz.exists() || !raiz.isDirectory) {
            Log.w("InicioApp", "No existe el directorio: ${raiz.absolutePath}")
            vistaSinVideos.visibility = View.VISIBLE
            return
        }

        val subcarpetas = raiz.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val listaVideos = mutableListOf<File>()

        for (carpeta in subcarpetas) {
            val videos = carpeta.listFiles()?.filter {
                it.isFile && it.extension.equals(
                    "mp4", ignoreCase = true
                ) && it.length() > 1_000_000
            } ?: continue
            listaVideos.addAll(videos)
        }

        if (listaVideos.isEmpty()) {
            vistaSinVideos.visibility = View.VISIBLE
            contenedor.removeAllViews()
            return
        }

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val hoy = formatoFecha.format(Date())
        val videosOrdenados = listaVideos.sortedByDescending { it.lastModified() }

        contenedor.removeAllViews()
        vistaSinVideos.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        val videosHoy = videosOrdenados.filter {
            formatoFecha.format(Date(it.lastModified())) == hoy
        }

        if (videosHoy.isNotEmpty()) {
            val videoDestacado = videosHoy.first()
            val vistaDestacada = inflater.inflate(R.layout.item_video_destacado, contenedor, false)
            cargarDatosEnVista(vistaDestacada, videoDestacado, formatoFecha)
            contenedor.addView(vistaDestacada)

            for (video in videosHoy.drop(1)) {
                Log.d("InicioApp", "Agregando video HOY (normal): ${video.name}")
                val vista = inflater.inflate(R.layout.item_video_normal, contenedor, false)
                cargarDatosEnVista(vista, video, formatoFecha)
                contenedor.addView(vista)
            }
        }

        val videosAnteriores = videosOrdenados.filter {
            formatoFecha.format(Date(it.lastModified())) != hoy
        }

        for (video in videosAnteriores) {
            Log.d("InicioApp", "Agregando video ANTERIOR: ${video.name}")
            val vista = inflater.inflate(R.layout.item_video_normal, contenedor, false)
            cargarDatosEnVista(vista, video, formatoFecha)
            contenedor.addView(vista)
        }
    }

    private fun cargarDatosEnVista(vista: View, video: File, formatoFecha: SimpleDateFormat) {
        val titulo = vista.findViewById<TextView>(R.id.txtTitulo)
        val fechaTxt = vista.findViewById<TextView>(R.id.txtFecha)
        val miniatura = vista.findViewById<ImageView>(R.id.imgMiniatura)
        val btnReproducir = vista.findViewById<ImageView>(R.id.btnReproducir)
        val btnExportarIcono = vista.findViewById<ImageView>(R.id.btnExportar)
        val btnEliminarIcono = vista.findViewById<ImageView>(R.id.btnEliminar)
        val btnEscuchar = vista.findViewById<ImageView?>(R.id.btnEscuchar)
        val escucharContenedor = vista.findViewById<View?>(R.id.escucharContainer)

        // Nombre limpio y fecha
        val nombreLimpio = video.nameWithoutExtension.replace("_", " ").replace("-", " ")
        titulo.text = nombreLimpio
        titulo.isSelected = true
        fechaTxt.text = formatoFecha.format(Date(video.lastModified()))

        // Miniatura
        Glide.with(this)
            .load(video.absolutePath)
            .thumbnail(0.1f)
            .into(miniatura)

        // Referencia al archivo original
        vista.tag = video

        // Reproducir
        btnReproducir.setOnClickListener {
            reproducirVideo(video)
        }

        // Exportar (ícono, obligatorio en todos)
        btnExportarIcono.setOnClickListener {
            exportarVideo(video)
        }

        // Exportar (contenedor, solo si existe en destacado)
        val exportarContenedor = vista.findViewById<View?>(R.id.exportarContainer)
        exportarContenedor?.setOnClickListener {
            exportarVideo(video)
        }

        // Eliminar (ícono)
        btnEliminarIcono.setOnClickListener {
            confirmarEliminacion(video)
        }

        // Eliminar (contenedor, solo en destacado)
        val eliminarContenedor = vista.findViewById<View?>(R.id.eliminarContainer)
        eliminarContenedor?.setOnClickListener {
            confirmarEliminacion(video)
        }

        // Renombrar al tocar vista
        vista.setOnClickListener {
            mostrarDialogoRenombrar(video, titulo, vista)
        }

        // Escuchar traducción
        btnEscuchar?.setOnClickListener {
            mostrarDialogoAudio(video)
        }
        escucharContenedor?.setOnClickListener {
            mostrarDialogoAudio(video)
        }
    }


    private fun exportarVideo(videoFile: File) {
        if (!videoFile.exists()) {
            Toast.makeText(this, "El archivo no existe", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.provider", videoFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Compartir video con"))
    }

    private fun reproducirVideo(videoFile: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            androidx.core.content.FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.provider", videoFile
            ), "video/mp4"
        )
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el video", Toast.LENGTH_LONG).show()
            Log.e("InicioApp", "Error al reproducir video: ${e.message}")
        }
    }

    private fun mostrarDialogoRenombrar(videoFile: File, tituloTextView: TextView, vista: View) {
        val view = layoutInflater.inflate(R.layout.dialog_renombrar_video, null)
        val edtNuevoNombre = view.findViewById<EditText>(R.id.edtNuevoNombre)
        val btnCancelar = view.findViewById<View>(R.id.btnCancelar)
        val btnAceptar = view.findViewById<View>(R.id.btnAceptar)

        edtNuevoNombre.setText(videoFile.nameWithoutExtension)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnAceptar.setOnClickListener {
            val nuevoNombre = edtNuevoNombre.text.toString().trim()
            if (nuevoNombre.isEmpty()) {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoArchivo = File(videoFile.parent, "$nuevoNombre.mp4")
            if (nuevoArchivo.exists()) {
                Toast.makeText(this, "Ya existe un video con ese nombre", Toast.LENGTH_LONG).show()
            } else {
                val renombrado = videoFile.renameTo(nuevoArchivo)
                if (renombrado) {
                    MediaScannerConnection.scanFile(
                        this, arrayOf(nuevoArchivo.absolutePath), arrayOf("video/mp4"), null
                    )
                    tituloTextView.text = nuevoNombre.replace("_", " ").replace("-", " ")
                    tituloTextView.isSelected = true

                    val btnReproducir = vista.findViewById<ImageView>(R.id.btnReproducir)
                    btnReproducir.setOnClickListener {
                        reproducirVideo(nuevoArchivo)
                    }

                    val btnExportar = vista.findViewById<LinearLayout>(R.id.exportarContainer)
                    btnExportar.setOnClickListener {
                        exportarVideo(nuevoArchivo)
                    }

                    vista.tag = nuevoArchivo

                    Toast.makeText(this, "Video renombrado correctamente", Toast.LENGTH_SHORT)
                        .show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "No se pudo renombrar el archivo", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        dialog.show()
    }

    private fun confirmarEliminacion(videoFile: File) {
        val view = layoutInflater.inflate(R.layout.dialog_confirmar_eliminacion, null)
        val btnCancelar = view.findViewById<View>(R.id.btnCancelarEliminar)
        val btnConfirmar = view.findViewById<View>(R.id.btnConfirmarEliminar)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmar.setOnClickListener {
            val carpeta = videoFile.parentFile
            if (carpeta != null && carpeta.exists()) {
                eliminarCarpetaCompleta(carpeta)
                Toast.makeText(this, "El video fue eliminado correctamente", Toast.LENGTH_SHORT)
                    .show()
                cargarVideosGrabados()
            } else {
                Toast.makeText(this, "No se encontró el video para eliminar", Toast.LENGTH_LONG)
                    .show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun eliminarCarpetaCompleta(carpeta: File): Boolean {
        if (carpeta.isDirectory) {
            val hijos = carpeta.listFiles()
            if (hijos != null) {
                for (archivo in hijos) {
                    if (archivo.isDirectory) {
                        eliminarCarpetaCompleta(archivo)
                    } else {
                        archivo.delete()
                    }
                }
            }
        }
        return carpeta.delete()
    }

    private fun mostrarDialogoAudio(videoFile: File) {
        val carpeta = videoFile.parentFile ?: return
        val archivoAudio = File(carpeta, "audio.mp3")

        if (!archivoAudio.exists()) {
            Toast.makeText(this, "No se encontró el audio de traducción", Toast.LENGTH_SHORT).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_escuchar_audio, null)
        val btnReproducir = view.findViewById<ImageView>(R.id.btnPlayAudio)
        val btnCerrar = view.findViewById<View>(R.id.btnCerrarAudio)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val mediaPlayer = android.media.MediaPlayer().apply {
            setDataSource(archivoAudio.absolutePath)
            prepare()
        }

        // ✅ Al terminar el audio, volver a mostrar el ícono de reproducir
        mediaPlayer.setOnCompletionListener {
            btnReproducir.setImageResource(R.drawable.reproducir)
            mediaPlayer.seekTo(0)
        }

        btnReproducir.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                mediaPlayer.seekTo(0)
                btnReproducir.setImageResource(R.drawable.reproducir)
            } else {
                mediaPlayer.start()
                btnReproducir.setImageResource(R.drawable.pausa)
            }
        }

        btnCerrar.setOnClickListener {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.release()
            dialog.dismiss()
        }

        dialog.show()
    }
}
