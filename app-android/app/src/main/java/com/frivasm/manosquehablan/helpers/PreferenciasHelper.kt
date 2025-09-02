package com.frivasm.manosquehablan.helpers

import android.content.Context

object PreferenciasHelper {
    private const val NOMBRE_PREF = "preferencias"

    fun guardarOrden(context: Context, tipo: String) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putString("orden", tipo).apply()
    }

    fun obtenerOrden(context: Context): String {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return prefs.getString("orden", "recientes") ?: "recientes"
    }

    // Preferencias para vista por fecha
    fun guardarMostrarFechaEnVista(context: Context, mostrar: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("mostrar_fecha_vista", mostrar).apply()
    }

    fun obtenerMostrarFechaEnVista(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean("mostrar_fecha_vista", true)
    }

    // Preferencias para vista alfabético
    fun guardarMostrarLetrasEnVista(context: Context, mostrar: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("mostrar_letras_vista", mostrar).apply()
    }

    fun obtenerMostrarLetrasEnVista(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean("mostrar_letras_vista", true)
    }

    // Preferencias compartidas
    fun guardarMostrarDetallesEnVista(context: Context, mostrar: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("mostrar_detalles_vista", mostrar).apply()
    }

    fun obtenerMostrarDetallesEnVista(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean("mostrar_detalles_vista", true)
    }

    fun guardarVistaCompacta(context: Context, compacta: Boolean) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("vista_compacta", compacta).apply()
    }

    fun obtenerVistaCompacta(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean("vista_compacta", false)
    }

    // Preferencias para diálogo de bienvenida
    fun marcarBienvenidaMostrada(context: Context) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bienvenida_mostrada", true).apply()
    }

    fun esPrimeraVez(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return !prefs.getBoolean("bienvenida_mostrada", false)
    }

    // Preferencias para recordatorio de grabación
    fun marcarRecordatorioGrabacionDeshabilitado(context: Context) {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("recordatorio_grabacion_deshabilitado", true).apply()
    }

    fun deberMostrarRecordatorioGrabacion(context: Context): Boolean {
        val prefs = context.getSharedPreferences(NOMBRE_PREF, Context.MODE_PRIVATE)
        return !prefs.getBoolean("recordatorio_grabacion_deshabilitado", false)
    }
}
