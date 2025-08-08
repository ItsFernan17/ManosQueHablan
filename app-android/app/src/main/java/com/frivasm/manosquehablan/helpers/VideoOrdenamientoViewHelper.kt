package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.frivasm.manosquehablan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object VideoOrdenamientoViewHelper {

    fun ordenarVideosPorFecha(
        context: Context,
        contenedor: LinearLayout,
        vistaSinVideos: View
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val raiz = File(context.getExternalFilesDir(null), "ManosQueHablan")
            val videos = raiz.listFiles()?.filter { it.isDirectory }?.flatMap { carpeta ->
                carpeta.listFiles()?.filter {
                    it.isFile && it.extension.equals("mp4", ignoreCase = true) && it.length() > 1_000_000
                } ?: emptyList()
            } ?: emptyList()

            val agrupados = OrdenamientoHelper.agruparVideosPorFecha(videos.sortedByDescending { it.lastModified() })
            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val fechaHoyFormateada = obtenerFechaHoy()

            withContext(Dispatchers.Main) {
                contenedor.removeAllViews()

                if (videos.isEmpty()) {
                    vistaSinVideos.visibility = View.VISIBLE
                    contenedor.addView(vistaSinVideos)
                    return@withContext
                }

                vistaSinVideos.visibility = View.GONE
                val inflater = LayoutInflater.from(context)

                for ((fecha, grupo) in agrupados) {
                    for ((index, videoOriginal) in grupo.withIndex()) {
                        val video = VideoSyncCache.videosActualizados[videoOriginal.absolutePath] ?: videoOriginal

                        val vista = inflater.inflate(R.layout.item_video_por_fecha, contenedor, false)

                        // Mostrar encabezado solo en el primer video del grupo
                        vista.findViewById<TextView>(R.id.txtEncabezadoFecha).apply {
                            text = if (fecha == fechaHoyFormateada) {
                                context.getString(R.string.videos_recientes_hoy)
                            } else {
                                context.getString(R.string.videos_anteriores)
                            }
                            visibility = if (index == 0) View.VISIBLE else View.GONE
                        }

                        VideoViewBuilder.construirVistaVideoNormal(context, vista, video, formatoFecha) { nuevoArchivo ->
                            vista.tag = nuevoArchivo
                            val nuevoTitulo = vista.findViewById<TextView>(R.id.txtTitulo)
                            nuevoTitulo.text = nuevoArchivo.nameWithoutExtension.replace("_", " ").replace("-", " ")
                            VideoSyncCache.videosActualizados[videoOriginal.absolutePath] = nuevoArchivo
                        }

                        contenedor.addView(vista)
                    }
                }
            }
        }
    }

    private fun obtenerFechaHoy(): String {
        val hoy = Calendar.getInstance().time
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(hoy)
    }
}
