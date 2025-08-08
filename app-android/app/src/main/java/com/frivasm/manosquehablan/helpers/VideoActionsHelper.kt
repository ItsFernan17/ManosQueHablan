package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object VideoActionsHelper {

    fun exportarVideo(context: Context, video: File) {
        if (!video.exists()) {
            Toast.makeText(context, "El archivo no existe", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", video)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.packageManager.queryIntentActivities(intent, 0).forEach {
            context.grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartir video con"))
    }

    fun reproducirVideo(context: Context, video: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", video)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir el video", Toast.LENGTH_LONG).show()
        }
    }

    fun eliminarVideoConConfirmacion(
        context: Context,
        archivo: File,
        onEliminado: () -> Unit
    ) {
        com.frivasm.manosquehablan.dialogs.DialogUtils.mostrarDialogoEliminar(context, archivo) {
            onEliminado()
        }
    }
}
