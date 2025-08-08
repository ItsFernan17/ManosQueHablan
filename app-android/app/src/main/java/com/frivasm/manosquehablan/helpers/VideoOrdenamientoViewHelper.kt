package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.utils.VideoUtils
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

            val videos = raiz.listFiles()
                ?.filter { it.isDirectory }
                ?.flatMap { carpeta ->
                    carpeta.listFiles()?.filter { f ->
                        f.isFile &&
                                f.extension.equals("mp4", ignoreCase = true) &&
                                f.length() > 1_000_000
                    } ?: emptyList()
                } ?: emptyList()

            val agrupados = OrdenamientoHelper.agruparVideosPorFecha(videos)

            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val inflater = LayoutInflater.from(context)

            // Label de mes actual para decidir "Este mes"
            val mesActualLabel = SimpleDateFormat("MMMM yyyy", Locale("es", "GT"))
                .format(Date()) // ej. "agosto 2025"

            withContext(Dispatchers.Main) {
                contenedor.removeAllViews()

                if (videos.isEmpty()) {
                    vistaSinVideos.visibility = View.VISIBLE
                    return@withContext
                }
                vistaSinVideos.visibility = View.GONE

                for ((claveGrupo, grupo) in agrupados) {
                    val grupoOrdenado = grupo.sortedByDescending { creationMillis(it) }

                    for ((index, videoOriginal) in grupoOrdenado.withIndex()) {
                        val video = VideoSyncCache.videosActualizados[videoOriginal.absolutePath] ?: videoOriginal
                        val vista = inflater.inflate(R.layout.item_video_por_fecha, contenedor, false)

                        // Encabezado: "Hoy" o "Este mes" si es el mes actual, si no el nombre del mes ("Agosto 2025")
                        val encabezadoTxt = when (claveGrupo) {
                            "Hoy" -> context.getString(R.string.videos_recientes_hoy)
                            mesActualLabel -> context.getString(R.string.videos_este_mes)
                            else -> claveGrupo.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                        }

                        vista.findViewById<TextView>(R.id.txtEncabezadoFecha).apply {
                            text = encabezadoTxt
                            visibility = if (index == 0) View.VISIBLE else View.GONE
                        }

                        VideoViewBuilder.construirVistaVideoNormal(
                            context, vista, video, formatoFecha
                        ) { nuevoArchivo ->
                            vista.tag = nuevoArchivo
                            val nuevoTitulo = vista.findViewById<TextView>(R.id.txtTitulo)
                            nuevoTitulo.text = nuevoArchivo.nameWithoutExtension
                                .replace("_", " ")
                                .replace("-", " ")
                            VideoSyncCache.videosActualizados[videoOriginal.absolutePath] = nuevoArchivo
                        }

                        // Línea divisoria / espacio antes del siguiente encabezado
                        val root = vista as LinearLayout
                        val dividerView = root.getChildAt(root.childCount - 1)
                        val esUltimoDelGrupo = index == (grupoOrdenado.size - 1)

                        if (esUltimoDelGrupo) {
                            dividerView.visibility = View.GONE
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.bottomMargin = 8.dpToPx(context)
                            vista.layoutParams = lp
                        } else {
                            dividerView.visibility = View.VISIBLE
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.bottomMargin = 0
                            vista.layoutParams = lp
                        }

                        contenedor.addView(vista)
                    }
                }
            }
        }
    }

    private fun creationMillis(file: File): Long {
        val raw = VideoUtils.obtenerFechaCreacionVideo(file.absolutePath)
        return if (raw < 1_000_000_000_000L) raw * 1000 else raw
    }

    private fun Int.dpToPx(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
