package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.helpers.VideoSyncCache
import com.frivasm.manosquehablan.helpers.VideoViewBuilder
import com.frivasm.manosquehablan.helpers.VideoStorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object VideoLoader {

    fun cargarVideosRecientes(
        context: Context,
        contenedor: LinearLayout,
        vistaSinVideos: View
    ) {
        // ✅ Limpiar cache de archivos que ya no existen
        VideoSyncCache.limpiarArchivosInexistentes()
        
        // Cargar desde ambos sistemas: viejo (externo) y nuevo (privado)
        val videosLegacy = cargarVideosLegacy(context)
        val videosNuevos = cargarVideosPrivados(context)
        
        val todosLosVideos = (videosLegacy + videosNuevos)
            .distinctBy { it.absolutePath } // Evitar duplicados
            .sortedByDescending { it.lastModified() } // Más recientes primero

        contenedor.removeAllViews()

        if (todosLosVideos.isEmpty()) {
            vistaSinVideos.visibility = View.VISIBLE
            // Iniciar animación de colores para el título si estamos en InicioAppActivity
            if (context is com.frivasm.manosquehablan.InicioAppActivity) {
                context.animarColoresTxtNoHayVideos()
            }
            return
        }

        vistaSinVideos.visibility = View.GONE
        // Detener animación de colores si estamos en InicioAppActivity
        if (context is com.frivasm.manosquehablan.InicioAppActivity) {
            context.detenerAnimacionColoresTxtNoHayVideos()
        }
        val inflater = LayoutInflater.from(context)
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        for ((index, videoOriginal) in todosLosVideos.withIndex()) {
            val archivoFinal = VideoSyncCache.videosActualizados[videoOriginal.absolutePath] ?: videoOriginal

            val layout = if (index == 0)
                R.layout.item_video_destacado
            else
                R.layout.item_video_normal

            val vista = inflater.inflate(layout, contenedor, false)

            VideoViewBuilder.construirVistaVideoNormal(context, vista, archivoFinal, formatoFecha) { nuevoArchivo ->
                // ✅ Actualizar silenciosamente el título y el tag sin recargar toda la vista
                vista.tag = nuevoArchivo
                val nuevoTitulo = vista.findViewById<TextView>(R.id.txtTitulo)
                nuevoTitulo.text = nuevoArchivo.nameWithoutExtension.replace("_", " ").replace("-", " ")

                // Guardar cambio en caché para que otras vistas reflejen el cambio sin recargar
                VideoSyncCache.videosActualizados[videoOriginal.absolutePath] = nuevoArchivo
            }

            contenedor.addView(vista)
        }
    }

    /**
     * Carga videos del sistema legacy (external files)
     */
    private fun cargarVideosLegacy(context: Context): List<File> {
        val raiz = File(context.getExternalFilesDir(null), "ManosQueHablan")
        if (!raiz.exists() || !raiz.isDirectory) {
            return emptyList()
        }

        return raiz.listFiles()?.filter { it.isDirectory }?.flatMap { carpeta ->
            carpeta.listFiles()?.filter {
                it.isFile && 
                it.extension.equals("mp4", ignoreCase = true) && 
                it.exists() && // ✅ Verificar que el archivo aún existe
                it.length() > 100_000
            } ?: emptyList()
        } ?: emptyList()
    }

    /**
     * Carga videos del nuevo sistema (private files)
     */
    private fun cargarVideosPrivados(context: Context): List<File> {
        return try {
            val storageManager = VideoStorageManager(context)
            // ✅ Filtrar solo archivos que aún existen
            storageManager.getAllSavedVideos().filter { it.exists() }
        } catch (e: Exception) {
            Log.w("VideoLoader", "Error cargando videos privados: ${e.message}")
            emptyList()
        }
    }
}
