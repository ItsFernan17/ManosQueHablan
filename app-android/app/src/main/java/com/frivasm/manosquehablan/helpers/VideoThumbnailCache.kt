package com.frivasm.manosquehablan.helpers

import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object VideoThumbnailCache {

    // Mapa de caché en memoria
    private val cache: MutableMap<String, Bitmap> = ConcurrentHashMap()

    // Obtener miniatura si ya está cacheada
    fun get(file: File): Bitmap? {
        return cache[file.absolutePath]
    }

    // Guardar una miniatura en la caché
    fun put(file: File, bitmap: Bitmap) {
        cache[file.absolutePath] = bitmap
    }

    // Eliminar la miniatura (por ejemplo, al borrar un video)
    fun remove(file: File) {
        cache.remove(file.absolutePath)
    }

    // Mover la miniatura al nuevo nombre (cuando el video se renombra)
    fun move(oldFile: File, newFile: File) {
        val oldKey = oldFile.absolutePath
        val newKey = newFile.absolutePath
        cache[oldKey]?.let {
            cache[newKey] = it
            cache.remove(oldKey)
        }
    }

    // Limpiar toda la caché (por si el usuario lo desea desde ajustes)
    fun clear() {
        cache.clear()
    }
}
