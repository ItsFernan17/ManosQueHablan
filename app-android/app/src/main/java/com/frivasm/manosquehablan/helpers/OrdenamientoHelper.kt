package com.frivasm.manosquehablan.helpers

import com.frivasm.manosquehablan.utils.VideoUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object OrdenamientoHelper {

    fun agruparVideosPorFecha(videos: List<File>): Map<String, List<File>> {
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val hoy = formatoFecha.format(Date())

        return videos.groupBy { video ->
            val fechaCreacion = VideoUtils.obtenerFechaCreacionVideo(video.absolutePath)
            val fechaFormateada = formatoFecha.format(Date(fechaCreacion))
            if (fechaFormateada == hoy) "Hoy" else fechaFormateada
        }.toSortedMap(compareByDescending { clave ->
            if (clave == "Hoy") System.currentTimeMillis()
            else formatoFecha.parse(clave)?.time ?: 0
        })
    }
}
