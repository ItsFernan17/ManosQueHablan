package com.frivasm.manosquehablan.models

import androidx.annotation.Keep

/**
 * Modelo de datos para las categorías del catálogo de señas
 */
@Keep
data class CategoriaSeña(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val boton_texto: String,
    val enlace: String
)

/**
 * Contenedor para el archivo JSON de categorías
 */
@Keep
data class CatalogoSenias(
    val categorias: List<CategoriaSeña>
)
