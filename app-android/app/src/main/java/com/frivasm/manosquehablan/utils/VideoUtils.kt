package com.frivasm.manosquehablan.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object VideoUtils {
    
    /**
     * Obtiene la fecha de creación de un video
     */
    fun obtenerFechaCreacionVideo(path: String): Long {
        val file = File(path)
        return if (file.exists()) {
            file.lastModified()
        } else {
            0L
        }
    }
    
    /**
     * Construye los detalles del video (tamaño, duración, etc.)
     */
    fun construirDetalles(
        context: android.content.Context,
        fecha: Date,
        path: String
    ): String {
        val file = File(path)
        if (!file.exists()) {
            return "Archivo no encontrado"
        }
        
        val tamaño = formatearTamaño(file.length())
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val fechaTexto = formatoFecha.format(fecha)
        
        return "$tamaño • $fechaTexto"
    }
    
    /**
     * Formatea el tamaño del archivo
     */
    private fun formatearTamaño(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
