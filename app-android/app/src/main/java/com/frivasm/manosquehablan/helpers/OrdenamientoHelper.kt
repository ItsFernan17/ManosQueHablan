package com.frivasm.manosquehablan.helpers

import com.frivasm.manosquehablan.utils.VideoUtils
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object OrdenamientoHelper {

    private val localeEsGT = Locale("es", "GT")

    fun agruparVideosPorFecha(videos: List<File>): Map<String, List<File>> {
        val zone = ZoneId.systemDefault()
        val hoy = LocalDate.now(zone)
        val fmtMes = DateTimeFormatter.ofPattern("MMMM yyyy", localeEsGT)

        val agrupado = videos.groupBy { video ->
            val raw = VideoUtils.obtenerFechaCreacionVideo(video.absolutePath)
            val millis = if (raw < 1_000_000_000_000L) raw * 1000 else raw
            val fechaLocal = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

            if (fechaLocal.isEqual(hoy)) {
                "Hoy"
            } else {
                fechaLocal.format(fmtMes) // "agosto 2025"
            }
        }

        val mesActualLabel = YearMonth.now(zone).format(fmtMes)

        val orden = Comparator<String> { a, b ->
            fun peso(k: String): Int = when (k) {
                "Hoy" -> 0
                mesActualLabel -> 1
                else -> 2
            }
            val pa = peso(a); val pb = peso(b)
            if (pa != pb) return@Comparator pa - pb

            // Para los meses, ordenar por YearMonth descendente
            if (pa == 2 || pa == 1) {
                val fmt = fmtMes
                val ya = YearMonth.parse(a, fmt)
                val yb = YearMonth.parse(b, fmt)
                return@Comparator yb.compareTo(ya)
            }
            0
        }

        return agrupado.toSortedMap(orden)
    }
}
