package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.SharedPreferences
import com.frivasm.manosquehablan.ConfiguracionActivity

/**
 * Helper para gestionar las configuraciones de la aplicación
 */
object ConfigHelper {
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("manos_que_hablan_config", Context.MODE_PRIVATE)
    }
    
    /**
     * Verifica si se debe mostrar el recordatorio al grabar
     */
    fun debeMostrarRecordatorio(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            ConfiguracionActivity.PREF_MOSTRAR_RECORDATORIO,
            ConfiguracionActivity.DEFAULT_MOSTRAR_RECORDATORIO
        )
    }
    
    /**
     * Verifica si se debe confirmar antes de compartir videos mal traducidos
     */
    fun debeConfirmarCompartirMalTraducidos(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            ConfiguracionActivity.PREF_CONFIRMAR_COMPARTIR_MAL_TRADUCIDOS,
            ConfiguracionActivity.DEFAULT_CONFIRMAR_COMPARTIR
        )
    }
    
    /**
     * Verifica si los sonidos están habilitados
     */
    fun sonidosHabilitados(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            ConfiguracionActivity.PREF_SONIDOS_HABILITADOS,
            ConfiguracionActivity.DEFAULT_SONIDOS
        )
    }
    
    /**
     * Deshabilita el recordatorio (cuando el usuario marca "No volver a mostrar")
     */
    fun deshabilitarRecordatorio(context: Context) {
        getPreferences(context).edit()
            .putBoolean(ConfiguracionActivity.PREF_MOSTRAR_RECORDATORIO, false)
            .apply()
    }
}
