package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.frivasm.manosquehablan.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object VideoLoader {

    fun cargarVideosRecientes(
        context: Context,
        contenedor: LinearLayout,
        vistaSinVideos: View
    ) {
        val raiz = File(context.getExternalFilesDir(null), "ManosQueHablan")
        if (!raiz.exists() || !raiz.isDirectory) {
            contenedor.removeAllViews()
            vistaSinVideos.visibility = View.VISIBLE
            return
        }

        val videos = raiz.listFiles()?.filter { it.isDirectory }?.flatMap { carpeta ->
            carpeta.listFiles()?.filter {
                it.isFile && it.extension.equals("mp4", ignoreCase = true) && it.length() > 100_000 // Reducido de 1MB a 100KB
            } ?: emptyList()
        } ?: emptyList()

        contenedor.removeAllViews()

        if (videos.isEmpty()) {
            vistaSinVideos.visibility = View.VISIBLE
            return
        }

        vistaSinVideos.visibility = View.GONE
        val inflater = LayoutInflater.from(context)
        val videosOrdenados = videos.sortedByDescending { it.lastModified() }
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        for ((index, videoOriginal) in videosOrdenados.withIndex()) {
            val archivoFinal = VideoSyncCache.videosActualizados[videoOriginal.absolutePath] ?: videoOriginal

            val layout = if (index == 0)
                R.layout.item_video_destacado
            else
                R.layout.item_video_normal

            val vista = inflater.inflate(layout, contenedor, false)

            VideoViewBuilder.construirVistaVideoNormal(context, vista, archivoFinal, formatoFecha) { nuevoArchivo ->
                // ✅ Actualizar silenciosamente el título y el tag sin recargar toda la vista
                vista.tag = nuevoArchivo
                val nuevoTitulo = vista.findViewById<TextView>(R.id.txtTitulo)
                nuevoTitulo.text = nuevoArchivo.nameWithoutExtension.replace("_", " ").replace("-", " ")

                // 🔁 Guardar cambio en caché para que otras vistas reflejen el cambio sin recargar
                VideoSyncCache.videosActualizados[videoOriginal.absolutePath] = nuevoArchivo
            }

            contenedor.addView(vista)
        }
    }
}
