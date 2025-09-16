package com.frivasm.manosquehablan.config

import com.frivasm.manosquehablan.BuildConfig

/**
 * Configuración central de servidor para Manos Que Hablan
 * 
 * Manejo dinámico de URLs según el entorno:
 * - DEBUG: Desarrollo local (192.168.1.13:5000)
 * - RELEASE: Producción (https://api.manosquehablan.com)
 */
object ServerConfig {
    
    /**
     * URL base principal del servidor
     */
    val BASE_URL: String get() = BuildConfig.BASE_URL
    
    /**
     * Configuraciones específicas para desarrollo
     */
    object Development {
        const val LOCAL_IP = "192.168.1.13"
        const val LOCAL_PORT = 8011
        const val LOCAL_URL = "http://$LOCAL_IP:$LOCAL_PORT/"
        
        // URLs alternativas para pruebas
        const val LOCALHOST_URL = "http://127.0.0.1:$LOCAL_PORT/"
        const val EMULATOR_URL = "http://10.0.2.2:$LOCAL_PORT/"
    }
    
    /**
     * Configuraciones para producción
     */
    object Production {
        const val DOMAIN = "api.manosquehablan.com"
        const val PRODUCTION_URL = "https://$DOMAIN/"
        
        // Backup/failover URLs si es necesario
        const val BACKUP_URL = "https://backup-api.manosquehablan.com/"
    }
    
    /**
     * Endpoints específicos de la API
     */
    object Endpoints {
        const val UPLOAD_VIDEO = "upload"
        const val PROCESS_VIDEO = "process" 
        const val DOWNLOAD_AUDIO = "download/audio"
        const val DOWNLOAD_TEXT = "download/text"
        const val HEALTH_CHECK = "health"
    }
    
    /**
     * Configuración de timeouts mejorados
     */
    object Timeouts {
        // Timeout para conectar al servidor (más generoso)
        const val CONNECT_TIMEOUT = 30L // segundos
        
        // Timeout para leer respuesta - muy generoso para procesamiento de video
        const val READ_TIMEOUT = 600L // 10 minutos (videos largos necesitan más tiempo)
        
        // Timeout para escribir/subir - generoso para videos grandes
        const val WRITE_TIMEOUT = 600L // 10 minutos
        
        // Timeout específico para verificar conectividad
        const val HEALTH_CHECK_TIMEOUT = 10L // segundos
        
        // Reintentos para verificación de servidor
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 2000L // 2 segundos entre reintentos
    }
    
    /**
     * Obtiene la URL completa para un endpoint específico
     */
    fun getEndpointUrl(endpoint: String): String {
        return BASE_URL.trimEnd('/') + "/" + endpoint.trimStart('/')
    }
    
    /**
     * Convierte una URL relativa en absoluta
     */
    fun makeAbsoluteUrl(relativeUrl: String): String {
        return if (relativeUrl.startsWith("http")) {
            relativeUrl
        } else {
            BASE_URL.trimEnd('/') + (if (relativeUrl.startsWith("/")) "" else "/") + relativeUrl
        }
    }
    
    /**
     * Verifica si estamos en modo desarrollo
     */
    val isDevelopment: Boolean get() = BuildConfig.DEBUG
    
    /**
     * Verifica si estamos en modo producción
     */
    val isProduction: Boolean get() = !BuildConfig.DEBUG
    
    /**
     * Información de debug del servidor actual
     */
    fun getServerInfo(): String {
        return buildString {
            appendLine("=== CONFIGURACIÓN DE SERVIDOR ===")
            appendLine("Modo: ${if (isDevelopment) "DESARROLLO" else "PRODUCCIÓN"}")
            appendLine("URL Base: $BASE_URL")
            appendLine("Logging: ${BuildConfig.ENABLE_LOGGING}")
            appendLine("====================================")
        }
    }
    
    /**
     * Verifica si el servidor está disponible
     */
    suspend fun verificarDisponibilidadServidor(): ServerStatus {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(Timeouts.HEALTH_CHECK_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(Timeouts.HEALTH_CHECK_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(Timeouts.HEALTH_CHECK_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url(getEndpointUrl(Endpoints.HEALTH_CHECK))
                .get()
                .build()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val response = client.newCall(request).execute()
                
                when {
                    response.isSuccessful -> ServerStatus.DISPONIBLE
                    response.code in 500..599 -> ServerStatus.ERROR_SERVIDOR
                    else -> ServerStatus.NO_DISPONIBLE
                }
            }
        } catch (e: java.net.ConnectException) {
            ServerStatus.SIN_CONEXION
        } catch (e: java.net.SocketTimeoutException) {
            ServerStatus.TIMEOUT
        } catch (e: Exception) {
            ServerStatus.ERROR_DESCONOCIDO
        }
    }
    
    /**
     * Estados posibles del servidor
     */
    enum class ServerStatus(val mensaje: String, val esDisponible: Boolean) {
        DISPONIBLE("Servidor disponible", true),
        NO_DISPONIBLE("Servidor no disponible", false),
        SIN_CONEXION("Sin conexión al servidor", false),
        TIMEOUT("El servidor no responde", false),
        ERROR_SERVIDOR("Error interno del servidor", false),
        ERROR_DESCONOCIDO("Error de conexión desconocido", false)
    }
}
