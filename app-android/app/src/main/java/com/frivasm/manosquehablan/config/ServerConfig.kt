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
        const val LOCAL_PORT = 8001
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
     * Configuración de timeouts
     */
    object Timeouts {
        const val CONNECT_TIMEOUT = 60L // segundos
        const val READ_TIMEOUT = 300L // 5 minutos
        const val WRITE_TIMEOUT = 300L // 5 minutos
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
}
