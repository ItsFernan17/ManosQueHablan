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
}
