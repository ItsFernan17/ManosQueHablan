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
                    Log.d("VideoFileHelper", "API exitoso, validando respuesta...")
                    val data = response.body()!!
                    
                    // Validar que todos los archivos necesarios estén disponibles
                    if (data.video_url.isNullOrBlank()) {
                        Log.e("VideoFileHelper", "El servidor no proporcionó URL del video")
                        ocultarCargando()
                        Toast.makeText(context, "Error: el servidor no generó el video", Toast.LENGTH_SHORT).show()
                        if (context is GrabarVideoActivity) {
                            context.restaurarVistaCamara()
                        }
                        return@launch
                    }
                    
                    if (data.audio_url.isNullOrBlank()) {
                        Log.e("VideoFileHelper", "El servidor no proporcionó URL del audio")
                        ocultarCargando()
                        Toast.makeText(context, "Error: el servidor no generó el audio", Toast.LENGTH_SHORT).show()
                        if (context is GrabarVideoActivity) {
                            context.restaurarVistaCamara()
                        }
                        return@launch
                    }
                    
                    if (data.texto_url.isNullOrBlank()) {
                        Log.e("VideoFileHelper", "El servidor no proporcionó URL del texto")
                        ocultarCargando()
                        Toast.makeText(context, "Error: el servidor no generó la transcripción", Toast.LENGTH_SHORT).show()
                        if (context is GrabarVideoActivity) {
                            context.restaurarVistaCamara()
                        }
                        return@launch
                    }
                    
                    try {
                        Log.d("VideoFileHelper", "Todos los archivos disponibles, guardando...")
                        guardarArchivosEnCarpeta(data)
                        ocultarCargando()
                        Toast.makeText(
                            context,
                            "Video guardado correctamente",
                            Toast.LENGTH_SHORT
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
                        Toast.makeText(context, "Error al descargar los archivos del servidor", Toast.LENGTH_SHORT).show()
                        // Restaurar vista de cámara para permitir nuevo intento
                        if (context is GrabarVideoActivity) {
                            context.restaurarVistaCamara()
                        }
                    }
                } else {
                    Log.e("VideoFileHelper", "API no exitoso: ${response.code()} - ${response.message()}")
                    ocultarCargando()
                    
                    // Solo mostrar toast para errores del servidor (400 y 500)
                    val codigoEstado = response.code()
                    if (codigoEstado >= 400 && codigoEstado < 600) {
                        Toast.makeText(
                            context, "Error del servidor", Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // Restaurar vista de cámara para permitir nuevo intento
                    if (context is GrabarVideoActivity) {
                        context.restaurarVistaCamara()
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoFileHelper", "Excepción durante llamada API: ${e.message}")
                ocultarCargando()
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
            
            // Validar que la URL no esté vacía
            if (url.isBlank()) {
                throw Exception("URL vacía para $nombreArchivo")
            }
            
            val request = okhttp3.Request.Builder().url(url).build()
            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e("VideoFileHelper", "Descarga fallida para $nombreArchivo: HTTP ${response.code}")
                throw Exception("El servidor no pudo proporcionar $nombreArchivo (HTTP ${response.code})")
            }
            
            if (response.body == null) {
                Log.e("VideoFileHelper", "Respuesta vacía para $nombreArchivo")
                throw Exception("El servidor devolvió contenido vacío para $nombreArchivo")
            }

            val archivoDestino = File(carpetaDestino, nombreArchivo)
            response.body!!.byteStream().use { input ->
                archivoDestino.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Verificar que el archivo se descargó correctamente
            if (!archivoDestino.exists() || archivoDestino.length() == 0L) {
                throw Exception("El archivo $nombreArchivo se descargó pero está vacío o corrupto")
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(archivoDestino.absolutePath),
                arrayOf(mimeType),
                null
            )

            Log.d("VideoFileHelper", "Archivo guardado exitosamente: ${archivoDestino.absolutePath} (${archivoDestino.length()} bytes)")
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
