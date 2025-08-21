package com.frivasm.manosquehablan.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiCliente {
    const val BASE_URL = "http://192.168.1.13:8001/" // Ajusta host/puerto

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Exponemos el client para reutilizar en descargas
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    val instance: ApiServicio by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiServicio::class.java)
    }

    fun urlAbsoluta(maybeRelative: String): String =
        if (maybeRelative.startsWith("http")) maybeRelative
        else BASE_URL.trimEnd('/') + (if (maybeRelative.startsWith("/")) "" else "/") + maybeRelative
}
