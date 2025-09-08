package com.frivasm.manosquehablan.helpers

import java.io.File

object VideoSyncCache {
    val videosActualizados = mutableMapOf<String, File>()
    
    /**
     * Limpia referencias a archivos que ya no existen para mantener la sincronización
     * con cambios externos (eliminaciones desde galería)
     */
    fun limpiarArchivosInexistentes() {
        val archivosAEliminar = videosActualizados.filter { (_, archivo) -> 
            !archivo.exists() 
        }.keys
        
        archivosAEliminar.forEach { ruta ->
            videosActualizados.remove(ruta)
        }
    }
}
