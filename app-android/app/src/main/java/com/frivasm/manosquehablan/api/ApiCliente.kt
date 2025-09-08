package com.frivasm.manosquehablan.api

import com.frivasm.manosquehablan.BuildConfig
import com.frivasm.manosquehablan.config.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiCliente {
    // Usar URL desde ServerConfig
    val BASE_URL = ServerConfig.BASE_URL

    private val logging = HttpLoggingInterceptor().apply {
        // Solo habilitar logging en debug builds
        level = if (BuildConfig.ENABLE_LOGGING) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // Exponemos el client para reutilizar en descargas
    val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(ServerConfig.Timeouts.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ServerConfig.Timeouts.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ServerConfig.Timeouts.WRITE_TIMEOUT, TimeUnit.SECONDS)
        
        // Solo agregar logging interceptor si está habilitado
        if (BuildConfig.ENABLE_LOGGING) {
            builder.addInterceptor(logging)
        }
        
        builder.build()
    }

    val instance: ApiServicio by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiServicio::class.java)
    }

    /**
     * Convierte una URL relativa o completa en absoluta
     */
    fun urlAbsoluta(maybeRelative: String): String = ServerConfig.makeAbsoluteUrl(maybeRelative)
}
