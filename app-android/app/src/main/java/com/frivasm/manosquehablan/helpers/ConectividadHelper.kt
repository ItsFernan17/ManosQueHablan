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

        // Mensajes más amigables para cada reintento
        val mensajesReintento = listOf(
            "Intentando reconectar...",
            "Un momento más...",
            "Casi listo..."
        )

        repeat(ServerConfig.Timeouts.MAX_RETRY_ATTEMPTS) { intento ->
            val resultado = ServerConfig.verificarDisponibilidadServidor()

            Log.d(TAG, "Intento ${intento + 1}: ${resultado.mensaje}")

            if (resultado.esDisponible) {
                onProgress("¡Conexión exitosa!")
                return@withContext resultado
            }

            // Si no es el último intento, esperar antes de reintentar
            if (intento < ServerConfig.Timeouts.MAX_RETRY_ATTEMPTS - 1) {
                // Usar mensaje amigable en lugar del contador
                val mensajeAmigable = mensajesReintento.getOrElse(intento) { "Reintentando..." }
                onProgress(mensajeAmigable)
                delay(ServerConfig.Timeouts.RETRY_DELAY_MS)
            }
        }

        // Si llegamos aquí, no se pudo conectar
        val ultimoResultado = ServerConfig.verificarDisponibilidadServidor()
        onProgress("No se pudo conectar al servidor")

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
                "Sin conexión a internet" to "No se detecta conexión de red. Activa Wi-Fi o datos móviles para continuar"

            ServerConfig.ServerStatus.TIMEOUT ->
                "Tiempo de espera agotado" to "El servidor está tardando en responder. Puede ser por congestión de red o mantenimiento"

            ServerConfig.ServerStatus.ERROR_SERVIDOR ->
                "Error interno del servidor" to "Hay un problema técnico en nuestros servidores. Estamos trabajando para solucionarlo"

            ServerConfig.ServerStatus.NO_DISPONIBLE ->
                "Servicio temporalmente suspendido" to "El servicio de traducción está en mantenimiento programado. Regresa en unos minutos"

            ServerConfig.ServerStatus.ERROR_DESCONOCIDO ->
                "Error inesperado" to "Ha ocurrido un problema no identificado. Intenta nuevamente o contacta soporte si persiste"
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
