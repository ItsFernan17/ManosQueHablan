package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.util.Log
import com.frivasm.manosquehablan.config.ServerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper para gestionar conectividad y estado del servidor
 */
class ConectividadHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "ConectividadHelper"
    }
    
    /**
     * Verifica la disponibilidad del servidor con reintentos
     */
    suspend fun verificarServidorConReintentos(
        onProgress: (String) -> Unit = {}
    ): ServerConfig.ServerStatus = withContext(Dispatchers.IO) {
        
        onProgress("Verificando conexión al servidor...")
        
        repeat(ServerConfig.Timeouts.MAX_RETRY_ATTEMPTS) { intento ->
            val resultado = ServerConfig.verificarDisponibilidadServidor()
            
            Log.d(TAG, "Intento ${intento + 1}: ${resultado.mensaje}")
            
            if (resultado.esDisponible) {
                onProgress("Servidor disponible")
                return@withContext resultado
            }
            
            // Si no es el último intento, esperar antes de reintentar
            if (intento < ServerConfig.Timeouts.MAX_RETRY_ATTEMPTS - 1) {
                onProgress("Reintentando conexión... (${intento + 2}/${ServerConfig.Timeouts.MAX_RETRY_ATTEMPTS})")
                delay(ServerConfig.Timeouts.RETRY_DELAY_MS)
            }
        }
        
        // Si llegamos aquí, no se pudo conectar
        val ultimoResultado = ServerConfig.verificarDisponibilidadServidor()
        onProgress("Error de conexión")
        
        ultimoResultado
    }
    
    /**
     * Obtiene un mensaje amigable según el estado del servidor
     */
    fun obtenerMensajeAmigable(status: ServerConfig.ServerStatus): Pair<String, String> {
        return when (status) {
            ServerConfig.ServerStatus.DISPONIBLE -> 
                "Conexión exitosa" to "El servidor está funcionando correctamente"
                
            ServerConfig.ServerStatus.SIN_CONEXION -> 
                "Sin conexión" to "Verifica tu conexión a internet e intenta nuevamente"

            ServerConfig.ServerStatus.TIMEOUT ->
                "Conexión lenta" to "La respuesta está tardando demasiado. Verifica tu conexión a internet o inténtalo de nuevo en unos instantes"

            ServerConfig.ServerStatus.ERROR_SERVIDOR ->
                "Problema del servidor" to "El servidor está experimentando problemas. Intenta más tarde"
                
            ServerConfig.ServerStatus.NO_DISPONIBLE -> 
                "Servidor no disponible" to "El servidor de traducción no está disponible en este momento"
                
            ServerConfig.ServerStatus.ERROR_DESCONOCIDO -> 
                "Error de conexión" to "Ocurrió un error inesperado. Verifica tu conexión e intenta nuevamente"
        }
    }
    
    /**
     * Determina si se debe mostrar el botón de reintentar
     */
    fun deberiaPermitirReintento(status: ServerConfig.ServerStatus): Boolean {
        return when (status) {
            ServerConfig.ServerStatus.SIN_CONEXION,
            ServerConfig.ServerStatus.TIMEOUT,
            ServerConfig.ServerStatus.NO_DISPONIBLE,
            ServerConfig.ServerStatus.ERROR_DESCONOCIDO -> true
            
            ServerConfig.ServerStatus.ERROR_SERVIDOR,
            ServerConfig.ServerStatus.DISPONIBLE -> false
        }
    }
}
