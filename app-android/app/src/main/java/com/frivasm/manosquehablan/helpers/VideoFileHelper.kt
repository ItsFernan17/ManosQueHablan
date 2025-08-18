package com.frivasm.manosquehablan.helpers

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.frivasm.manosquehablan.GrabarVideoActivity
import com.frivasm.manosquehablan.InicioAppActivity
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.api.ApiCliente
import com.frivasm.manosquehablan.api.RespuestaProcesamiento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoFileHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val layoutInflater: android.view.LayoutInflater
) {
    
    private lateinit var progressDialog: AlertDialog
    
    fun enviarVideoAPI(path: String) {
        val videoFile = File(path)
        val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

        mostrarCargando()

        lifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiCliente.instance.procesarVideo(body)
                }
                
                Log.d("VideoFileHelper", "Respuesta del API: ${response.code()} - ${response.message()}")
                
                if (response.isSuccessful && response.body() != null) {
                    Log.d("VideoFileHelper", "API exitoso, guardando archivos...")
                    try {
                        guardarArchivosEnCarpeta(response.body()!!)
                        ocultarCargando()
                        Toast.makeText(
                            context,
                            "Archivos guardados correctamente",
                            Toast.LENGTH_LONG
                        ).show()
                        // Pequeño delay antes de redirigir
                        delay(1500)
                        context.startActivity(Intent(context, InicioAppActivity::class.java))
                        if (context is android.app.Activity) {
                            context.finish()
                        }
                    } catch (e: Exception) {
                        Log.e("VideoFileHelper", "Error al guardar archivos: ${e.message}")
                        ocultarCargando()
                        Toast.makeText(
                            context, "Error al guardar archivos. Intenta nuevamente", Toast.LENGTH_LONG
                        ).show()
                        // Restaurar vista de cámara para permitir nuevo intento
                        if (context is GrabarVideoActivity) {
                            context.restaurarVistaCamara()
                        }
                    }
                } else {
                    Log.e("VideoFileHelper", "API no exitoso: ${response.code()} - ${response.message()}")
                    ocultarCargando()
                    Toast.makeText(
                        context, "Error del servidor. Intenta nuevamente", Toast.LENGTH_LONG
                    ).show()
                    // Restaurar vista de cámara para permitir nuevo intento
                    if (context is GrabarVideoActivity) {
                        context.restaurarVistaCamara()
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoFileHelper", "Excepción durante llamada API: ${e.message}")
                ocultarCargando()
                Toast.makeText(context, "Error de conexión. Intenta nuevamente", Toast.LENGTH_LONG)
                    .show()
                // Restaurar vista de cámara para permitir nuevo intento
                if (context is GrabarVideoActivity) {
                    context.restaurarVistaCamara()
                }
            }
        }
    }
    
    private suspend fun guardarArchivosEnCarpeta(data: RespuestaProcesamiento) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("VideoFileHelper", "Iniciando guardado de archivos...")
                val fecha = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val nombreCarpeta = "ManosQueHablan/Sesion_$fecha"
                val carpeta = File(context.getExternalFilesDir(null), nombreCarpeta)
                if (!carpeta.exists()) carpeta.mkdirs()
                
                Log.d("VideoFileHelper", "Carpeta creada: ${carpeta.absolutePath}")

                // Guardar video con nombre personalizado
                Log.d("VideoFileHelper", "Guardando video...")
                guardarArchivoLocal(
                    ApiCliente.BASE_URL + data.video_url.removePrefix("/"),
                    "video_${fecha}.mp4",
                    "video/mp4",
                    carpeta
                )

                // Mantener audio y transcripción igual
                Log.d("VideoFileHelper", "Guardando audio...")
                guardarArchivoLocal(
                    ApiCliente.BASE_URL + data.audio_url.removePrefix("/"),
                    "audio.mp3",
                    "audio/mpeg",
                    carpeta
                )
                
                Log.d("VideoFileHelper", "Guardando transcripción...")
                guardarArchivoLocal(
                    ApiCliente.BASE_URL + data.texto_url.removePrefix("/"),
                    "transcripcion.txt",
                    "text/plain",
                    carpeta
                )
                
                Log.d("VideoFileHelper", "Todos los archivos guardados exitosamente")
            } catch (e: Exception) {
                Log.e("VideoFileHelper", "Error en guardarArchivosEnCarpeta: ${e.message}")
                throw e // Re-lanzar la excepción para que se maneje en el nivel superior
            }
        }

    private suspend fun guardarArchivoLocal(
        url: String, nombreArchivo: String, mimeType: String, carpetaDestino: File
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d("VideoFileHelper", "Descargando $nombreArchivo desde: $url")
            val request = okhttp3.Request.Builder().url(url).build()
            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful || response.body == null) {
                Log.e("VideoFileHelper", "Descarga fallida para $nombreArchivo: ${response.code}")
                throw Exception("Descarga fallida para $nombreArchivo: ${response.code}")
            }

            val archivoDestino = File(carpetaDestino, nombreArchivo)
            response.body!!.byteStream().use { input ->
                archivoDestino.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(archivoDestino.absolutePath),
                arrayOf(mimeType),
                null
            )

            Log.d("VideoFileHelper", "Archivo guardado exitosamente: ${archivoDestino.absolutePath}")
        } catch (e: Exception) {
            Log.e("VideoFileHelper", "Error al guardar $nombreArchivo: ${e.message}")
            throw e // Re-lanzar la excepción para que se maneje en el nivel superior
        }
    }
    
    private fun mostrarCargando() {
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)
        builder.setView(layoutInflater.inflate(R.layout.dialog_loading, null))
        progressDialog = builder.create()
        progressDialog.show()
    }

    private fun ocultarCargando() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }
}
