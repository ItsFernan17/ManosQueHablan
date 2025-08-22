package com.frivasm.manosquehablan.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class RespuestaProcesamiento(
    val video_url: String,
    val audio_url: String,
    val texto_url: String
)

interface ApiServicio {
    @Multipart
    @POST("test")
    suspend fun procesarVideo(
        @Part video: MultipartBody.Part
    ): Response<RespuestaProcesamiento>
}