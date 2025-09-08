package com.frivasm.manosquehablan.models

/**
 * Modelo de datos para las categorías del catálogo de señas
 */
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
data class CatalogoSenias(
    val categorias: List<CategoriaSeña>
)
