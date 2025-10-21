package com.frivasm.manosquehablan.models

import androidx.annotation.Keep
import com.google.gson.*
import java.lang.reflect.Type

/**
 * Modelo de datos para un enlace individual
 */
@Keep
data class EnlaceSeña(
    val texto: String,
    val url: String
)

/**
 * Modelo de datos para las categorías del catálogo de señas
 * Soporta tanto enlaces únicos como múltiples para máxima flexibilidad
 */
@Keep
data class CategoriaSeña(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val boton_texto: String? = null, // Para retrocompatibilidad
    val enlace: String? = null, // Para retrocompatibilidad
    val enlaces: List<EnlaceSeña>? = null // Para múltiples enlaces
) {
    /**
     * Obtiene todos los enlaces disponibles, normalizando formato único y múltiple
     */
    fun obtenerEnlaces(): List<EnlaceSeña> {
        return when {
            // Si hay enlaces múltiples, usar esos
            !enlaces.isNullOrEmpty() -> enlaces
            // Si hay enlace único, convertirlo a formato múltiple
            !enlace.isNullOrBlank() -> listOf(
                EnlaceSeña(
                    texto = boton_texto ?: "Ver Vocabulario",
                    url = enlace
                )
            )
            // Sin enlaces disponibles
            else -> emptyList()
        }
    }
    
    /**
     * Verifica si la categoría tiene enlaces válidos
     */
    fun tieneEnlaces(): Boolean = obtenerEnlaces().isNotEmpty()
    
    /**
     * Obtiene la cantidad de enlaces disponibles
     */
    fun cantidadEnlaces(): Int = obtenerEnlaces().size
}

/**
 * Contenedor para el archivo JSON de categorías
 */
@Keep
data class CatalogoSenias(
    val categorias: List<CategoriaSeña>
)
