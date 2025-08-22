package com.frivasm.manosquehablan.dialogs

import android.app.AlertDialog
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.frivasm.manosquehablan.R
import java.io.File

object DialogUtils {

    fun mostrarDialogoRenombrar(
        context: Context,
        videoFile: File,
        tituloTextView: TextView,
        vista: View,
        onRenombrado: (File) -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_renombrar_video, null)
        val edtNuevoNombre = view.findViewById<EditText>(R.id.edtNuevoNombre)
        val btnCancelar = view.findViewById<View>(R.id.btnCancelar)
        val btnAceptar = view.findViewById<View>(R.id.btnAceptar)

        edtNuevoNombre.setText(videoFile.nameWithoutExtension)

        val dialog = AlertDialog.Builder(context).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnAceptar.setOnClickListener {
            val nuevoNombre = edtNuevoNombre.text.toString().trim()
            if (nuevoNombre.isEmpty()) {
                Toast.makeText(context, "El nombre no puede estar vacio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoArchivo = File(videoFile.parent, "$nuevoNombre.mp4")
            if (nuevoArchivo.exists()) {
                Toast.makeText(context, "Ya existe un video con ese nombre", Toast.LENGTH_SHORT)
                    .show()
            } else {
                if (videoFile.renameTo(nuevoArchivo)) {
                    // Actualizar index del sistema (antiguo y nuevo)
                    MediaScannerConnection.scanFile(
                        context, arrayOf(videoFile.absolutePath), arrayOf("video/mp4"), null
                    )
                    MediaScannerConnection.scanFile(
                        context, arrayOf(nuevoArchivo.absolutePath), arrayOf("video/mp4"), null
                    )

                    // Actualizar referencia interna de la vista
                    vista.tag = nuevoArchivo

                    // ⚠️ No se modifica directamente el título aquí: dejar que onRenombrado lo haga visualmente
                    onRenombrado(nuevoArchivo)
                    
                    // Toast de éxito al cambiar nombre
                    Toast.makeText(context, "Nombre cambiado correctamente", Toast.LENGTH_SHORT).show()
                    
                    dialog.dismiss()
                } else {
                    // Mantener toast de error al renombrar
                    Toast.makeText(context, "No se pudo renombrar", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }


    fun mostrarDialogoAudio(context: Context, audioFile: File) {
        if (!audioFile.exists()) {
            return
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_escuchar_audio, null)
        val btnReproducir = view.findViewById<ImageView>(R.id.btnPlayAudio)
        val btnCerrar = view.findViewById<View>(R.id.btnCerrarAudio)

        val dialog = AlertDialog.Builder(context).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            prepare()
        }

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

    fun mostrarDialogoEliminar(
        context: Context,
        carpeta: File,
        onConfirmado: () -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_confirmar_eliminacion, null)
        val btnCancelar = view.findViewById<View>(R.id.btnCancelarEliminar)
        val btnConfirmar = view.findViewById<View>(R.id.btnConfirmarEliminar)

        val dialog = AlertDialog.Builder(context).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnConfirmar.setOnClickListener {
            if (carpeta.exists()) {
                carpeta.deleteRecursively()
                Toast.makeText(context, "Video eliminado correctamente", Toast.LENGTH_SHORT)
                    .show()
                onConfirmado()
            } else {
                // Solo log, sin toast para este error
                onConfirmado()
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}
