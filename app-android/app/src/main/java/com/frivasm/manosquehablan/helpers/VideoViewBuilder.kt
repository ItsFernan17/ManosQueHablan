package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.dialogs.DialogUtils
import com.frivasm.manosquehablan.utils.VideoUtils
import com.frivasm.manosquehablan.helpers.VideoViewOptionsHelper
import com.frivasm.manosquehablan.helpers.VideoThumbnailCache
import com.frivasm.manosquehablan.helpers.VideoActionsHelper
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.media.ThumbnailUtils

object VideoViewBuilder {

    fun construirVistaVideoNormal(
        context: Context,
        vista: View,
        video: File,
        formatoFecha: SimpleDateFormat,
        onRecargar: (File) -> Unit
    ) {
        construirVistaVideoNormal(context, vista, video, formatoFecha, onRecargar, true, true, false)
    }

    fun construirVistaVideoNormal(
        context: Context,
        vista: View,
        video: File,
        formatoFecha: SimpleDateFormat,
        onRecargar: (File) -> Unit,
        mostrarFecha: Boolean,
        mostrarDetalles: Boolean,
        vistaCompacta: Boolean,
        tipoVista: String = ""
    ) {
        val titulo = vista.findViewById<TextView>(R.id.txtTitulo)
        val detalles = vista.findViewById<TextView?>(R.id.txtDetalles)
        val fecha = vista.findViewById<TextView?>(R.id.txtFecha) // ✅ Verificamos si existe el campo de fecha

        val miniatura = vista.findViewById<ImageView>(R.id.imgMiniatura)
        val btnReproducir = vista.findViewById<ImageView>(R.id.btnReproducir)
        val btnOpciones = vista.findViewById<ImageView?>(R.id.btnOpciones) // Botón de opciones

        val btnEscuchar = vista.findViewById<ImageView?>(R.id.btnEscuchar)
        val escucharContenedor = vista.findViewById<View?>(R.id.escucharContainer)
        val btnVerTraduccion = vista.findViewById<ImageView?>(R.id.btnVerTraduccion)
        val verTraduccionContenedor = vista.findViewById<View?>(R.id.verTraduccionContainer)
        val btnExportar = vista.findViewById<ImageView?>(R.id.btnExportar)
        val exportarContenedor = vista.findViewById<View?>(R.id.exportarContainer)
        val btnEliminar = vista.findViewById<ImageView?>(R.id.btnEliminar)
        val eliminarContenedor = vista.findViewById<View?>(R.id.eliminarContainer)

        fun asignarListeners(videoFile: File) {
            vista.tag = videoFile

            // ✅ Mostrar título actualizado
            titulo.text = videoFile.nameWithoutExtension.replace("_", " ").replace("-", " ")
            titulo.isSelected = true // Para marquee

            // ✅ Mostrar fecha de creación si existe el TextView y está habilitado
            if (fecha != null && mostrarFecha) {
                fecha.text = try {
                    val fechaCreacion = VideoUtils.obtenerFechaCreacionVideo(videoFile.absolutePath)
                    formatoFecha.format(Date(fechaCreacion))
                } catch (_: Exception) {
                    ""
                }
                fecha.visibility = View.VISIBLE
            } else {
                fecha?.visibility = View.GONE
            }

            // ✅ Detalles (fecha + peso) si existe el TextView txtDetalles y está habilitado
            if (detalles != null && mostrarDetalles) {
                CoroutineScope(Dispatchers.Main).launch {
                    val detallesTexto = withContext(Dispatchers.IO) {
                        VideoUtils.construirDetalles(
                            context,
                            Date(videoFile.lastModified()),
                            videoFile.absolutePath
                        )
                    }
                    detalles.text = detallesTexto
                    detalles.visibility = View.VISIBLE
                }
            } else {
                detalles?.visibility = View.GONE
            }

            // ✅ Miniatura desde caché o generar
            val cachedBitmap = VideoThumbnailCache.get(videoFile)
            if (cachedBitmap != null) {
                miniatura.setImageBitmap(cachedBitmap)
                miniatura.clearAnimation() // Detener animación si existe
            } else {
                miniatura.setImageResource(R.drawable.video_loading_placeholder)
                // Agregar animación shimmer mientras carga
                miniatura.startAnimation(android.view.animation.AnimationUtils.loadAnimation(context, R.anim.shimmer_placeholder))
                CoroutineScope(Dispatchers.Main).launch {
                    val thumbnail = withContext(Dispatchers.IO) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // API 29+
                                ThumbnailUtils.createVideoThumbnail(videoFile, Size(320, 240), null)
                            } else {
                                // < API 29 — usar MediaMetadataRetriever y recortar al tamaño deseado
                                val retriever = android.media.MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(videoFile.absolutePath)
                                    val frame = retriever.frameAtTime // frame por defecto (≈ primer frame)
                                    frame?.let {
                                        ThumbnailUtils.extractThumbnail(
                                            it,
                                            320,
                                            240,
                                            ThumbnailUtils.OPTIONS_RECYCLE_INPUT
                                        )
                                    }
                                } finally {
                                    retriever.release()
                                }
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    thumbnail?.let {
                        VideoThumbnailCache.put(videoFile, it)
                        miniatura.clearAnimation() // Detener animación cuando carga la miniatura real
                        miniatura.setImageBitmap(it)
                    }
                }
            }

            // ✅ Acciones
            miniatura.setOnClickListener {
                VideoActionsHelper.reproducirVideo(context, videoFile)
            }

            btnReproducir.setOnClickListener {
                VideoActionsHelper.reproducirVideo(context, videoFile)
            }

            btnEscuchar?.setOnClickListener {
                val audio = File(videoFile.parentFile, "audio_traducido.mp3")
                DialogUtils.mostrarDialogoAudio(context, audio)
            }

            escucharContenedor?.setOnClickListener {
                val audio = File(videoFile.parentFile, "audio_traducido.mp3")
                DialogUtils.mostrarDialogoAudio(context, audio)
            }

            btnVerTraduccion?.setOnClickListener {
                val transcripcion = File(videoFile.parentFile, "transcripcion.txt")
                DialogUtils.mostrarDialogoTranscripcion(context, transcripcion)
            }

            verTraduccionContenedor?.setOnClickListener {
                val transcripcion = File(videoFile.parentFile, "transcripcion.txt")
                DialogUtils.mostrarDialogoTranscripcion(context, transcripcion)
            }

            btnExportar?.setOnClickListener {
                VideoActionsHelper.compartirVideo(context, videoFile)
            }

            exportarContenedor?.setOnClickListener {
                VideoActionsHelper.compartirVideo(context, videoFile)
            }

            btnEliminar?.setOnClickListener {
                DialogUtils.mostrarDialogoEliminar(context, videoFile.parentFile ?: videoFile) {
                    // Si el contexto es una actividad, animar la eliminación del video
                    if (context is com.frivasm.manosquehablan.InicioAppActivity) {
                        context.runOnUiThread {
                            context.eliminarVideoConAnimacion(vista, videoFile)
                        }
                    } else {
                        // Para otros contextos, usar el callback de recarga
                        onRecargar(videoFile)
                    }
                }
            }

            eliminarContenedor?.setOnClickListener {
                DialogUtils.mostrarDialogoEliminar(context, videoFile.parentFile ?: videoFile) {
                    // Si el contexto es una actividad, animar la eliminación del video
                    if (context is com.frivasm.manosquehablan.InicioAppActivity) {
                        context.runOnUiThread {
                            context.eliminarVideoConAnimacion(vista, videoFile)
                        }
                    } else {
                        // Para otros contextos, usar el callback de recarga
                        onRecargar(videoFile)
                    }
                }
            }

            // ✅ Renombrar y refrescar datos
            titulo.setOnClickListener {
                DialogUtils.mostrarDialogoRenombrar(context, videoFile, titulo, vista) { nuevoArchivo ->
                    VideoThumbnailCache.move(videoFile, nuevoArchivo)
                    asignarListeners(nuevoArchivo)
                    onRecargar(nuevoArchivo)
                }
            }

            // ✅ Botón de opciones - Solo funciona en vistas específicas
            btnOpciones?.setOnClickListener {
                when (tipoVista) {
                    "fecha" -> VideoViewOptionsHelper.mostrarOpcionesPorFecha(context, videoFile)
                    "alfabetico" -> VideoViewOptionsHelper.mostrarOpcionesAlfabetico(context, videoFile)
                    else -> {
                        // Determinar automáticamente el tipo de vista si no se especifica
                        val encabezadoFecha = vista.findViewById<TextView?>(R.id.txtEncabezadoFecha)
                        val encabezadoLetra = vista.findViewById<TextView?>(R.id.txtEncabezadoLetra)
                        
                        when {
                            encabezadoFecha != null -> VideoViewOptionsHelper.mostrarOpcionesPorFecha(context, videoFile)
                            encabezadoLetra != null -> VideoViewOptionsHelper.mostrarOpcionesAlfabetico(context, videoFile)
                            // Si no está en una vista específica, no hacer nada o mostrar mensaje
                        }
                    }
                }
            }
        }

        // ✅ Carga inicial
        asignarListeners(video)
    }

    fun construirVistaVideoPorFecha(
        context: Context,
        vista: View,
        video: File,
        formatoFecha: SimpleDateFormat,
        onRecargar: (File) -> Unit,
        mostrarEncabezadoFecha: Boolean = true
    ) {
        // Ocultar el encabezado de fecha si no se requiere (para búsqueda)
        val encabezadoFecha = vista.findViewById<TextView?>(R.id.txtEncabezadoFecha)
        if (!mostrarEncabezadoFecha) {
            encabezadoFecha?.visibility = View.GONE
        }

        // Usar la lógica existente para construir el video
        construirVistaVideoNormal(
            context = context,
            vista = vista,
            video = video,
            formatoFecha = formatoFecha,
            onRecargar = onRecargar,
            mostrarFecha = true,
            mostrarDetalles = true,
            vistaCompacta = false,
            tipoVista = "fecha"
        )
    }
}
