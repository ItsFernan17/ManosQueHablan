package com.frivasm.manosquehablan.helpers

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.frivasm.manosquehablan.InicioAppActivity
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.api.ApiCliente
import com.frivasm.manosquehablan.api.RespuestaProcesamiento
import kotlinx.coroutines.Dispatchers
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
                if (response.isSuccessful && response.body() != null) {
                    guardarArchivosEnCarpeta(response.body()!!)
                    ocultarCargando()
                    Toast.makeText(
                        context,
                        "Archivos guardados correctamente",
                        Toast.LENGTH_LONG
                    ).show()
                    context.startActivity(Intent(context, InicioAppActivity::class.java))
                    if (context is android.app.Activity) {
                        context.finish()
                    }
                } else {
                    ocultarCargando()
                    Toast.makeText(
                        context, "Error al procesar el video", Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                ocultarCargando()
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
    
    private suspend fun guardarArchivosEnCarpeta(data: RespuestaProcesamiento) =
        withContext(Dispatchers.IO) {
            val fecha = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val nombreCarpeta = "ManosQueHablan/Sesion_$fecha"
            val carpeta = File(context.getExternalFilesDir(null), nombreCarpeta)
            if (!carpeta.exists()) carpeta.mkdirs()

            // Guardar video con nombre personalizado
            guardarArchivoLocal(
                ApiCliente.BASE_URL + data.video_url.removePrefix("/"),
                "video_${fecha}.mp4",
                "video/mp4",
                carpeta
            )

            // Mantener audio y transcripción igual
            guardarArchivoLocal(
                ApiCliente.BASE_URL + data.audio_url.removePrefix("/"),
                "audio.mp3",
                "audio/mpeg",
                carpeta
            )
            guardarArchivoLocal(
                ApiCliente.BASE_URL + data.texto_url.removePrefix("/"),
                "transcripcion.txt",
                "text/plain",
                carpeta
            )
        }

    private suspend fun guardarArchivoLocal(
        url: String, nombreArchivo: String, mimeType: String, carpetaDestino: File
    ) = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) throw Exception("Descarga fallida")

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

            Log.d("GuardarArchivo", "Guardado: ${archivoDestino.absolutePath}")
        } catch (e: Exception) {
            Log.e("GuardarArchivo", "Error al guardar $nombreArchivo: ${e.message}")
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
