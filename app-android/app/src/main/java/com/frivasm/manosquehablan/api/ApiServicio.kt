package com.frivasm.manosquehablan.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class RespuestaProcesamiento(
    val video_url: String,
    val audio_url: String,
    val texto_url: String
)

data class RespuestaHealth(
    val status: String,
    val timestamp: Long,
    val version: String?
)

interface ApiServicio {
    @Multipart
    @POST("upload_video")
    suspend fun procesarVideo(
        @Part video: MultipartBody.Part
    ): Response<RespuestaProcesamiento>
    
    @GET("health")
    suspend fun verificarSalud(): Response<RespuestaHealth>
}