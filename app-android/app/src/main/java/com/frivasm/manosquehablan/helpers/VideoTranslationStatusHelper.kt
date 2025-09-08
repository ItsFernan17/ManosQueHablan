package com.frivasm.manosquehablan.helpers

import android.content.Context
import java.io.File

/**
 * Helper para gestionar el estado de traducción de videos
 * Marca videos que fallaron en la traducción (sin audio/texto)
 */
object VideoTranslationStatusHelper {

    private const val BAD_TRANSLATION_MARKER = ".mal_traducido"

    /**
     * Marca un video como mal traducido de forma asíncrona para evitar bloqueos
     */
    fun marcarVideoComoMalTraducido(videoFile: File) {
        // Ejecutar operación I/O en thread background para evitar bloqueos
        Thread {
            val parentDir = videoFile.parentFile ?: return@Thread
            val markerFile = File(parentDir, BAD_TRANSLATION_MARKER)
            try {
                if (!markerFile.exists()) {
                    markerFile.createNewFile()
                }
            } catch (e: Exception) {
                // Ignorar errores de marcado, no es crítico
            }
        }.start()
    }

    /**
     * Verifica si un video está marcado como mal traducido
     * También detecta automáticamente videos sin traducción completa
     */
    fun esVideoMalTraducido(videoFile: File): Boolean {
        val parentDir = videoFile.parentFile ?: return false
        val markerFile = File(parentDir, BAD_TRANSLATION_MARKER)
        
        // Si está explícitamente marcado como mal traducido
        if (markerFile.exists()) {
            return true
        }
        
        // Si no tiene traducción completa (archivos de audio y texto faltantes)
        return !tieneTraduccionCompleta(videoFile)
    }

    /**
     * Remueve la marca de mal traducido de un video
     * (útil si el usuario vuelve a procesar el video)
     */
    fun removerMarcaMalTraducido(videoFile: File) {
        val parentDir = videoFile.parentFile ?: return
        val markerFile = File(parentDir, BAD_TRANSLATION_MARKER)
        try {
            if (markerFile.exists()) {
                markerFile.delete()
            }
        } catch (e: Exception) {
            // Ignorar errores, no es crítico
        }
    }

    /**
     * Verifica si existen los archivos de traducción (audio y texto)
     * Busca tanto en la carpeta del video como en carpetas relacionadas
     */
    fun tieneTraduccionCompleta(videoFile: File): Boolean {
        val parentDir = videoFile.parentFile ?: return false
        
        // Buscar archivos de traducción en la misma carpeta del video
        val audioFile = File(parentDir, "audio_traducido.mp3")
        val textoFile = File(parentDir, "transcripcion.txt")
        
        val tieneAudio = audioFile.exists() && audioFile.length() > 0
        val tieneTexto = textoFile.exists() && textoFile.length() > 0
        
        // Verificar que el contenido del texto sea válido (no "Sin detecciones válidas")
        if (tieneTexto) {
            try {
                val contenidoTexto = textoFile.readText(Charsets.UTF_8).trim()
                if (contenidoTexto.equals("Sin detecciones válidas.", ignoreCase = true) ||
                    contenidoTexto.equals("Sin detecciones validas.", ignoreCase = true)) {
                    return false // No es una traducción completa válida
                }
            } catch (e: Exception) {
                // Si hay error leyendo, considerar que el archivo existe pero puede estar corrupto
                return false
            }
        }
        
        return tieneAudio && tieneTexto
    }
    
    /**
     * Obtiene información detallada sobre el estado de traducción
     */
    fun obtenerEstadoTraduccion(videoFile: File): EstadoTraduccion {
        val parentDir = videoFile.parentFile ?: return EstadoTraduccion.SIN_ARCHIVOS
        
        val audioFile = File(parentDir, "audio_traducido.mp3")
        val textoFile = File(parentDir, "transcripcion.txt")
        val markerFile = File(parentDir, BAD_TRANSLATION_MARKER)
        
        val tieneAudio = audioFile.exists() && audioFile.length() > 0
        val tieneTexto = textoFile.exists() && textoFile.length() > 0
        val estaMarcado = markerFile.exists()
        
        return when {
            estaMarcado -> EstadoTraduccion.MARCADO_COMO_MALO
            tieneAudio && tieneTexto -> EstadoTraduccion.TRADUCCION_COMPLETA
            tieneAudio && !tieneTexto -> EstadoTraduccion.SOLO_AUDIO
            !tieneAudio && tieneTexto -> EstadoTraduccion.SOLO_TEXTO
            else -> EstadoTraduccion.SIN_ARCHIVOS
        }
    }
    
    /**
     * Estados posibles de traducción de un video
     */
    enum class EstadoTraduccion {
        TRADUCCION_COMPLETA,    // Tiene audio y texto
        SOLO_AUDIO,            // Solo tiene archivo de audio
        SOLO_TEXTO,            // Solo tiene archivo de texto
        SIN_ARCHIVOS,          // No tiene archivos de traducción
        MARCADO_COMO_MALO      // Explícitamente marcado como mal traducido
    }
    
    /**
     * Verifica si hay contenido de audio válido disponible para reproducir
     * (independientemente del estado de mal traducido)
     */
    fun tieneAudioDisponible(videoFile: File): Boolean {
        val parentDir = videoFile.parentFile ?: return false
        val audioFile = File(parentDir, "audio_traducido.mp3")
        return audioFile.exists() && audioFile.length() > 0
    }
    
    /**
     * Verifica si hay contenido de texto válido disponible para mostrar
     * (independientemente del estado de mal traducido)
     */
    fun tieneTextoDisponible(videoFile: File): Boolean {
        val parentDir = videoFile.parentFile ?: return false
        val textoFile = File(parentDir, "transcripcion.txt")
        return textoFile.exists() && textoFile.length() > 0
    }
    
    /**
     * Verifica si el contenido del texto es válido para mostrar
     * (no está vacío ni contiene respuestas inválidas del servidor)
     */
    fun esTextoValido(videoFile: File): Boolean {
        val parentDir = videoFile.parentFile ?: return false
        val textoFile = File(parentDir, "transcripcion.txt")
        
        if (!textoFile.exists() || textoFile.length() == 0L) return false
        
        return try {
            val contenido = textoFile.readText(Charsets.UTF_8).trim()
            // El texto es válido si no está vacío y no contiene respuestas inválidas del servidor
            contenido.isNotEmpty() && 
            !contenido.equals("Sin detecciones válidas.", ignoreCase = true) &&
            !contenido.equals("Sin detecciones validas.", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verifica si el audio es válido para reproducir
     * Si el texto indica "Sin detecciones válidas", el audio también se considera inválido
     */
    fun esAudioValido(videoFile: File): Boolean {
        val parentDir = videoFile.parentFile ?: return false
        val audioFile = File(parentDir, "audio_traducido.mp3")
        val textoFile = File(parentDir, "transcripcion.txt")
        
        // Verificar que el archivo de audio exista y tenga contenido
        if (!audioFile.exists() || audioFile.length() == 0L) return false
        
        // Si el texto indica "Sin detecciones válidas", el audio tampoco es válido
        if (textoFile.exists()) {
            try {
                val contenidoTexto = textoFile.readText(Charsets.UTF_8).trim()
                if (contenidoTexto.equals("Sin detecciones válidas.", ignoreCase = true) ||
                    contenidoTexto.equals("Sin detecciones validas.", ignoreCase = true)) {
                    return false // Audio inválido si el texto indica sin detecciones
                }
            } catch (e: Exception) {
                // Si hay error leyendo el texto, solo verificar que el audio exista
            }
        }
        
        return true
    }
}
