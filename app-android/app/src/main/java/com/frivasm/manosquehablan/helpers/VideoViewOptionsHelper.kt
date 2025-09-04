package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.dialogs.DialogUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

object VideoViewOptionsHelper {

    private fun mostrarOpcionesVideo(context: Context, videoFile: File, layoutRes: Int) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(layoutRes, null)
        dialog.setContentView(view)

        val opcionCompartir = view.findViewById<LinearLayout>(R.id.opcion_compartir)
        val opcionVerTraduccion = view.findViewById<LinearLayout>(R.id.opcion_ver_traduccion)
        val opcionEscuchar = view.findViewById<LinearLayout>(R.id.opcion_escuchar)
        val opcionEliminar = view.findViewById<LinearLayout>(R.id.opcion_eliminar)

        // Compartir Video
        opcionCompartir?.setOnClickListener {
            VideoActionsHelper.compartirVideo(context, videoFile)
            dialog.dismiss()
        }

        // Ver Texto Traducido
        opcionVerTraduccion?.setOnClickListener {
            videoFile.parentFile?.let { parentDir ->
                val transcripcion = File(parentDir, "transcripcion.txt")
                DialogUtils.mostrarDialogoTranscripcion(context, transcripcion)
            }
            dialog.dismiss()
        }

        // Escuchar Traducción
        opcionEscuchar?.setOnClickListener {
            videoFile.parentFile?.let { parentDir ->
                val audio = File(parentDir, "audio_traducido.mp3")
                DialogUtils.mostrarDialogoAudio(context, audio)
            }
            dialog.dismiss()
        }

        // Eliminar Video
        opcionEliminar?.setOnClickListener {
            dialog.dismiss()
            DialogUtils.mostrarDialogoEliminar(context, videoFile.parentFile ?: videoFile) {
                // Si el contexto es una actividad, animar la eliminación del video
                if (context is com.frivasm.manosquehablan.InicioAppActivity) {
                    context.runOnUiThread {
                        // Buscar la vista del video en el contenedor para animarla
                        val contenedor = context.findViewById<android.widget.LinearLayout>(R.id.contenedorVideos)
                        for (i in 0 until contenedor.childCount) {
                            val vista = contenedor.getChildAt(i)
                            if (vista.tag == videoFile) {
                                context.eliminarVideoConAnimacion(vista, videoFile)
                                break
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    // Método para mantener compatibilidad con las llamadas existentes
    fun mostrarOpcionesPorFecha(context: Context, videoFile: File) {
        mostrarOpcionesVideo(context, videoFile, R.layout.bottom_sheet_opciones_fecha)
    }

    fun mostrarOpcionesAlfabetico(context: Context, videoFile: File) {
        mostrarOpcionesVideo(context, videoFile, R.layout.bottom_sheet_opciones_alfabetico)
    }
}
