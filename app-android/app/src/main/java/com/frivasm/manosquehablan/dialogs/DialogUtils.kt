package com.frivasm.manosquehablan.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.frivasm.manosquehablan.GrabarVideoActivity
import com.frivasm.manosquehablan.R
import java.io.File

object DialogUtils {

    // DESHABILITADO - Función auxiliar para animar títulos (manteniendo diseño natural)
    // fun animarTituloColores(context: Context, textView: TextView) {
    //     // Función comentada para evitar efectos visuales innecesarios
    // }
    
    // Función auxiliar para establecer color estático de títulos
    fun establecerColorTituloEstatico(context: Context, textView: TextView) {
        val colorRojo = androidx.core.content.ContextCompat.getColor(context, R.color.rojo)
        textView.setTextColor(colorRojo) // Color estático rojo
    }

    // Función auxiliar para transformar texto de transcripción
    fun transformarTextoTranscripcion(texto: String): String {
        if (texto.isBlank()) return texto

        // Limpiar espacios extra y dividir en palabras
        val palabras = texto.trim().replace(Regex("\\s+"), " ").split(" ")

        // Procesar cada palabra: quitar puntos existentes y convertir a minúsculas
        val palabrasLimpias = palabras.map { palabra ->
            palabra.replace(".", "").lowercase()
        }.filter { it.isNotEmpty() }

        if (palabrasLimpias.isEmpty()) return ""

        // Capitalizar primera letra de la primera palabra
        val palabrasFormateadas = palabrasLimpias.mapIndexed { index, palabra ->
            if (index == 0 && palabra.isNotEmpty()) {
                palabra.first().uppercase() + palabra.drop(1)
            } else {
                palabra
            }
        }

        // Unir las palabras y agregar punto solo al final
        return palabrasFormateadas.joinToString(" ") + "."
    }

    // Funciones auxiliares para el recordatorio de grabar
    fun mostrarPrimerDialogoRecordatorio(context: Context, onSiguiente: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_recordatorio_grabar, null)
        val btnSiguiente = view.findViewById<View>(R.id.btnSiguienteRecordatorio)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloRecordatorio)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSiguiente.setOnClickListener {
            dialog.dismiss()
            onSiguiente() // Ir al segundo diálogo
        }

        // Título con color estático (sin animación)
        establecerColorTituloEstatico(context, txtTitulo)

        // DESHABILITADO - Cancelar animación cuando el diálogo se cierre
        dialog.setOnDismissListener {
            // Ya no hay animación que cancelar
        }

        dialog.show()
    }

    fun mostrarDialogoUsoResponsable(context: Context, onSiguiente: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_uso_responsable, null)
        val btnSiguiente = view.findViewById<LinearLayout>(R.id.btnSiguienteUsoResponsable)
        // El título ahora está dentro del ScrollView > LinearLayout > TextView
        val scrollView = (view as LinearLayout).getChildAt(0) as android.widget.ScrollView
        val contentLayout = scrollView.getChildAt(0) as LinearLayout
        val txtTitulo = contentLayout.getChildAt(0) as TextView

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSiguiente.setOnClickListener {
            dialog.dismiss()
            onSiguiente()
        }

        // Título con color estático (sin animación)
        establecerColorTituloEstatico(context, txtTitulo)

        // DESHABILITADO - Cancelar animación cuando el diálogo se cierre
        dialog.setOnDismissListener {
            // Ya no hay animación que cancelar
        }

        dialog.show()
    }

    fun mostrarSegundoDialogoPosicion(context: Context, onContinuar: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_posicion_usuario, null)
        val btnEntendido = view.findViewById<View>(R.id.btnEntendidoPosicion)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloPosicion)
        val checkNoMostrarMas = view.findViewById<android.widget.CheckBox>(R.id.checkNoMostrarMasPosicion)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnEntendido.setOnClickListener {
            // Si el checkbox está marcado, guardar la preferencia
            if (checkNoMostrarMas.isChecked) {
                com.frivasm.manosquehablan.helpers.ConfigHelper.deshabilitarRecordatorio(context)
            }

            dialog.dismiss()
            onContinuar() // Continuar con la navegación a la cámara
        }

        // Animación del título con colores rojo y celeste
        establecerColorTituloEstatico(context, txtTitulo)

        // Cancelar animación cuando el diálogo se cierre
        dialog.setOnDismissListener {
            // Ya no hay animación que cancelar
        }

        dialog.show()
    }

    // Funciones principales de diálogos
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
                Toast.makeText(context, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoArchivo = File(videoFile.parent, "$nuevoNombre.mp4")
            if (nuevoArchivo.exists()) {
                Toast.makeText(context, "Ya existe un video con ese nombre", Toast.LENGTH_SHORT).show()
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

                    // No se modifica directamente el título aquí: dejar que onRenombrado lo haga visualmente
                    onRenombrado(nuevoArchivo)

                    dialog.dismiss()

                    // Toast de éxito después de cerrar el dialog para mejor timing
                    Toast.makeText(context, "Nombre cambiado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    // Toast de error al renombrar
                    Toast.makeText(context, "No se pudo renombrar", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    fun mostrarDialogoAudio(context: Context, audioFile: File) {
        if (!audioFile.exists()) {
            Toast.makeText(context, "Audio no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_escuchar_audio, null)
        val btnReproducir = view.findViewById<ImageView>(R.id.btnPlayAudio)
        val btnCerrar = view.findViewById<View>(R.id.btnCerrarAudio)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloEscucharAudio)

        val dialog = AlertDialog.Builder(context).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Animar colores del título
        establecerColorTituloEstatico(context, txtTitulo)

        var mediaPlayer: MediaPlayer? = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
            }

            mediaPlayer.setOnCompletionListener {
                btnReproducir.setImageResource(R.drawable.reproducir)
                mediaPlayer?.seekTo(0)
            }

            btnReproducir.setOnClickListener {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        player.seekTo(0)
                        btnReproducir.setImageResource(R.drawable.reproducir)
                    } else {
                        player.start()
                        btnReproducir.setImageResource(R.drawable.pausa)
                    }
                }
            }

            btnCerrar.setOnClickListener {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) player.stop()
                    player.release()
                }
                dialog.dismiss()
            }

            dialog.show()

        } catch (e: Exception) {
            mediaPlayer?.release()
            return
        }
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
                dialog.dismiss()
                onConfirmado()
                // Toast después de cerrar el dialog y actualizar la UI
                Toast.makeText(context, "Video eliminado correctamente", Toast.LENGTH_SHORT).show()
            } else {
                // Solo log, sin toast para este error
                dialog.dismiss()
                onConfirmado()
            }
        }

        dialog.show()
    }

    fun mostrarDialogoAcercaDe(context: Context) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_acerca_de, null)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloAcercaDe)
        val btnAceptar = view.findViewById<LinearLayout>(R.id.btnAceptar)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Botón para cerrar el diálogo
        btnAceptar.setOnClickListener {
            dialog.dismiss()
        }

        // Animación del título con colores rojo y celeste
        establecerColorTituloEstatico(context, txtTitulo)

        // Cancelar animación cuando el diálogo se cierre
        dialog.setOnDismissListener {
            // Ya no hay animación que cancelar
        }

        dialog.show()
    }


    fun mostrarDialogoTerminosUso(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.manosquehablan.org/terminos-uso.html"))
        context.startActivity(intent)
    }

    fun mostrarDialogoPreguntasFrecuentes(context: Context) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_preguntas_frecuentes, null)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloPreguntasFrecuentes)
        val btnAceptar = view.findViewById<LinearLayout>(R.id.btnAceptarPreguntasFrecuentes)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Botón para cerrar el diálogo
        btnAceptar.setOnClickListener {
            dialog.dismiss()
        }

        // Animación del título con colores rojo y celeste
        establecerColorTituloEstatico(context, txtTitulo)

        // Cancelar animación cuando el diálogo se cierre
        dialog.setOnDismissListener {
            // Ya no hay animación que cancelar
        }

        dialog.show()
    }

    fun mostrarDialogoTranscripcion(context: Context, archivoTranscripcion: File) {
        if (!archivoTranscripcion.exists()) {
            Toast.makeText(context, "Transcripción no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.dialog_ver_transcripcion, null)
            val txtTranscripcion = view.findViewById<TextView>(R.id.txtTranscripcion)
            val btnCerrar = view.findViewById<View>(R.id.btnCerrarTranscripcion)
            val txtTitulo = view.findViewById<TextView>(R.id.txtTituloTextoTraducido)

            val dialog = AlertDialog.Builder(context).setView(view).setCancelable(false).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Animar colores del título
            establecerColorTituloEstatico(context, txtTitulo)

            // Leer el contenido del archivo de transcripción
            val contenidoOriginal = archivoTranscripcion.readText(Charsets.UTF_8)

            // Transformar el texto para que se vea como oración
            val textoTransformado = transformarTextoTranscripcion(contenidoOriginal)
            txtTranscripcion.text = textoTransformado

            btnCerrar.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

        } catch (e: Exception) {
            // Si hay error, no mostrar nada (igual que el diálogo de audio)
            return
        }
    }

    fun mostrarDialogoBienvenida(context: Context) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_bienvenida, null)
        val btnAceptar = view.findViewById<View>(R.id.btnAceptarBienvenida)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloBienvenida)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnAceptar.setOnClickListener {
            // Marcar que ya se mostró la bienvenida
            com.frivasm.manosquehablan.helpers.PreferenciasHelper.marcarBienvenidaMostrada(context)
            dialog.dismiss()
        }

        // Animación del título con colores rojo y celeste
        establecerColorTituloEstatico(context, txtTitulo)

        // Cancelar animación cuando el diálogo se cierre
        dialog.setOnDismissListener {
            // Ya no hay animación que cancelar
        }

        dialog.show()
    }

    fun mostrarDialogoRecordatorioGrabar(context: Context, onContinuar: () -> Unit) {
        // Mostrar primer diálogo con recordatorios
        mostrarPrimerDialogoRecordatorio(context) {
            // Cuando se presiona "Siguiente", mostrar diálogo de uso responsable
            mostrarDialogoUsoResponsable(context) {
                // Después del uso responsable, mostrar segundo diálogo con imagen de posición
                mostrarSegundoDialogoPosicion(context, onContinuar)
            }
        }
    }

    fun mostrarDialogoPosicionUsuario(context: Context, onContinuar: () -> Unit) {
        mostrarSegundoDialogoPosicion(context, onContinuar)
    }

    /**
     * Muestra diálogo de confirmación para compartir videos mal traducidos
     */
    fun mostrarDialogoConfirmarCompartirMalTraducido(
        context: Context,
        onConfirmar: () -> Unit,
        onCancelar: (() -> Unit)? = null
    ) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_confirmar_compartir_mal_traducido, null)

        val btnCancelar = view.findViewById<View>(R.id.btnCancelarCompartir)
        val btnConfirmar = view.findViewById<View>(R.id.btnConfirmarCompartir)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloConfirmarCompartir)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Animar colores del título
        establecerColorTituloEstatico(context, txtTitulo)

        btnCancelar.setOnClickListener {
            dialog.dismiss()
            onCancelar?.invoke()
        }

        btnConfirmar.setOnClickListener {
            dialog.dismiss()
            onConfirmar()
        }

        dialog.show()
    }
}
