package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.FileProvider
import java.io.File

@ExperimentalCamera2Interop
object VideoActionsHelper {

    fun compartirVideo(context: Context, video: File) {
        if (!video.exists()) {
            Toast.makeText(context, "Video no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TEXT, "Video compartido desde Manos Que Hablan")
            }
            
            val chooser = Intent.createChooser(intent, "Compartir video")
            
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "No hay aplicación para compartir", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("VideoActionsHelper", "Error compartiendo video: ${e.message}")
            Toast.makeText(context, "Error al compartir video", Toast.LENGTH_SHORT).show()
        }
    }

    fun reproducirVideo(context: Context, video: File) {
        if (!video.exists()) {
            Toast.makeText(context, "Video no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Usar FileProvider para generar URI segura
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
            
            // Crear intent para abrir con reproductor del sistema
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Agregar flag para forzar mostrar el selector cada vez
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            
            // Verificar si hay múltiples apps que pueden manejar videos
            val packageManager = context.packageManager
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            
            if (resolveInfos.size > 1) {
                // Hay múltiples apps - forzar selector con chooser personalizado
                val chooser = Intent.createChooser(intent, "Reproducir video con:")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } else if (resolveInfos.size == 1) {
                // Solo hay una app - abrir directamente
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No hay aplicación para reproducir videos", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("VideoActionsHelper", "Error abriendo reproductor: ${e.message}")
            Toast.makeText(context, "Error al reproducir video", Toast.LENGTH_SHORT).show()
        }
    }

    fun abrirCarpetaVideos(context: Context, video: File) {
        try {
            val carpeta = video.parentFile ?: return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", carpeta)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Busca la carpeta 'ManosQueHablan' en tu Galería", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Busca la carpeta 'ManosQueHablan' en tu Galería", Toast.LENGTH_LONG).show()
        }
    }

    fun eliminarVideoConConfirmacion(
        context: Context,
        archivo: File,
        vista: android.view.View? = null,
        onEliminado: () -> Unit
    ) {
        com.frivasm.manosquehablan.dialogs.DialogUtils.mostrarDialogoEliminar(context, archivo) {
            // Llamar al callback proporcionado
            onEliminado()
            
            // Si el contexto es una actividad y tenemos la vista, animar la eliminación
            if (context is com.frivasm.manosquehablan.InicioAppActivity && vista != null) {
                context.runOnUiThread {
                    context.eliminarVideoConAnimacion(vista, archivo)
                }
            } else if (context is com.frivasm.manosquehablan.InicioAppActivity) {
                // Si no tenemos la vista, recargar normalmente
                context.runOnUiThread {
                    context.aplicarOrdenamiento(com.frivasm.manosquehablan.helpers.PreferenciasHelper.obtenerOrden(context))
                }
            }
        }
    }
}
