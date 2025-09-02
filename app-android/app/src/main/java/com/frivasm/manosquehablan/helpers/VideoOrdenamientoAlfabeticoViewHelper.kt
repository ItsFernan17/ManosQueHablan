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
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.*

object VideoOrdenamientoAlfabeticoViewHelper {

    fun ordenarVideosPorLetra(
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
                                f.length() > 100_000 // Cambiado de 1MB a 100KB para consistencia
                    } ?: emptyList()
                } ?: emptyList()

            val inflater = LayoutInflater.from(context)
            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Collator español para ordenar por título (respeta acentos/ñ)
            val collator = Collator.getInstance(Locale("es", "ES")).apply {
                strength = Collator.PRIMARY
            }

            data class Item(val file: File, val titulo: String)

            fun tituloDe(file: File): String {
                val vigente = VideoSyncCache.videosActualizados[file.absolutePath] ?: file
                return vigente.nameWithoutExtension
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
            }

            fun letraInicial(t: String): String {
                val ch = t.firstOrNull { it.isLetter() } ?: return "#"
                return ch.toString().uppercase(Locale("es", "ES"))
            }

            val items = videos.map { Item(it, tituloDe(it)) }

            // Orden global A–Z por título
            val itemsOrdenados = items.sortedWith { a, b ->
                collator.compare(a.titulo, b.titulo)
            }

            // Precalcular letras para saber última de cada bloque
            val letras = itemsOrdenados.map { letraInicial(it.titulo) }

            withContext(Dispatchers.Main) {
                contenedor.removeAllViews()

                if (itemsOrdenados.isEmpty()) {
                    vistaSinVideos.visibility = View.VISIBLE
                    return@withContext
                }
                vistaSinVideos.visibility = View.GONE

                // Cargar preferencias de configuración
                val mostrarLetras = PreferenciasHelper.obtenerMostrarLetrasEnVista(context)
                val mostrarDetalles = PreferenciasHelper.obtenerMostrarDetallesEnVista(context)
                val vistaCompacta = PreferenciasHelper.obtenerVistaCompacta(context)

                var letraActual: String? = null

                itemsOrdenados.forEachIndexed { idx, item ->
                    val vigente = VideoSyncCache.videosActualizados[item.file.absolutePath] ?: item.file
                    val letra = letras[idx]

                    val vista = inflater.inflate(R.layout.item_video_por_letra, contenedor, false)

                    // Encabezado: solo cuando cambia la letra y está habilitado
                    val mostrarEncabezado = letra != letraActual && mostrarLetras
                    letraActual = letra

                    vista.findViewById<TextView>(R.id.txtEncabezadoLetra).apply {
                        text = if (letra == "#") "•" else letra
                        visibility = if (mostrarEncabezado) View.VISIBLE else View.GONE
                    }
                    // Mostrar/ocultar la línea bajo el chip de letra
                    vista.findViewById<View>(R.id.lineaEncabezadoLetra)?.apply {
                        visibility = if (mostrarEncabezado) View.VISIBLE else View.GONE
                    }

                    // Construir contenido del item (miniatura, título, detalles, etc.)
                    VideoViewBuilder.construirVistaVideoNormal(
                        context,
                        vista,
                        vigente,
                        formatoFecha,
                        { nuevoArchivo ->
                            // Callback tras renombrar
                            val nuevoTitulo = nuevoArchivo.nameWithoutExtension
                                .replace("_", " ")
                                .replace("-", " ")
                                .trim()

                            val letraNueva = letraInicial(nuevoTitulo)

                            // Actualiza cache de sincronización
                            VideoSyncCache.videosActualizados[item.file.absolutePath] = nuevoArchivo

                            if (letraNueva != letra) {
                                // Si cambió de letra, reconstruimos la lista para mover el item al bloque correcto
                                contenedor.post {
                                    ordenarVideosPorLetra(context, contenedor, vistaSinVideos)
                                }
                            } else {
                                // Misma letra: solo actualizamos el título en la vista actual
                                val tvTitulo = vista.findViewById<TextView>(R.id.txtTitulo)
                                tvTitulo.text = nuevoTitulo
                            }
                        },
                        mostrarLetras, // Este parámetro controla si mostrar encabezados (no se usa en el VideoViewBuilder)
                        mostrarDetalles,
                        vistaCompacta,
                        "alfabetico"
                    )

                    // Línea divisoria entre items: ocultar si es la última del bloque de la letra
                    val divider = vista.findViewById<View>(R.id.lineaDivisoria)
                    val esUltimoDeLetra = (idx == itemsOrdenados.lastIndex) ||
                            (letras[idx + 1] != letra)

                    if (esUltimoDeLetra) {
                        divider.visibility = View.GONE
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.bottomMargin = 8.dpToPx(context)
                        vista.layoutParams = lp
                    } else {
                        divider.visibility = View.VISIBLE
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

    private fun Int.dpToPx(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
