package com.frivasm.manosquehablan.utils

import android.content.Context
import android.media.MediaPlayer
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.media.MediaMetadataRetriever
import java.text.SimpleDateFormat
import java.util.*

object VideoUtils {

    suspend fun obtenerDuracion(ruta: String): String = withContext(Dispatchers.IO) {
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(ruta)
            mediaPlayer.prepare()
            val duracionMs = mediaPlayer.duration
            mediaPlayer.release()

            val segundos = duracionMs / 1000
            String.format("%02d:%02d", segundos / 60, segundos % 60)
        } catch (e: Exception) {
            "00:00"
        }
    }

    suspend fun construirDetalles(context: Context, fecha: Date, ruta: String): String {
        val formatoHora = SimpleDateFormat("h:mm a", Locale.getDefault())
        val hora = formatoHora.format(fecha)
        val tamano = Formatter.formatShortFileSize(context, File(ruta).length())
        val duracion = obtenerDuracion(ruta)
        return "$hora · $tamano · $duracion"
    }

    fun obtenerFechaCreacionVideo(path: String): Long {
        return File(path).lastModified()
    }

}
