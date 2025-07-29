package com.frivasm.manosquehablan

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.frivasm.manosquehablan.api.ApiCliente
import com.frivasm.manosquehablan.api.RespuestaProcesamiento
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GrabarVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGrabarVideoBinding
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var camSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var timer: Timer? = null
    private var segundosGrabados = 0
    private lateinit var progressDialog: AlertDialog

    private val REQUEST_CODE_PERMISOS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGrabarVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPermisos()

        if (allPermissionsGranted()) {
            iniciarCamara()
        }

        binding.btnGrabar.setOnClickListener {
            if (recording != null) detenerGrabacion() else iniciarGrabacion()
        }

        binding.btnRotarCamara.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK
            iniciarCamara()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun verificarPermisos() {
        val permisos = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permisos.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permisos.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val noOtorgados = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (noOtorgados.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                noOtorgados.toTypedArray(),
                REQUEST_CODE_PERMISOS
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        val permisos = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        return permisos.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISOS && allPermissionsGranted()) {
            iniciarCamara()
        } else {
            Toast.makeText(this, "Permisos requeridos para continuar", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            camSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, camSelector, preview, videoCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun iniciarGrabacion() {
        val outputOptions = FileOutputOptions.Builder(createTempFile()).build()
        val currentCapture = videoCapture ?: return
        recording = currentCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        this@GrabarVideoActivity,
                        Manifest.permission.RECORD_AUDIO
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> iniciarTemporizador()
                    is VideoRecordEvent.Finalize -> {
                        detenerTemporizador()
                        if (!event.hasError()) {
                            enviarVideoAPI(event.outputResults.outputUri.path ?: return@start)
                        } else {
                            Toast.makeText(this, "Error al grabar video", Toast.LENGTH_SHORT).show()
                        }
                        recording = null
                    }
                }
            }
    }

    private fun createTempFile(): File {
        return File.createTempFile("temp_video", ".mp4", cacheDir)
    }

    private fun detenerGrabacion() {
        recording?.stop()
        recording = null
    }

    private fun iniciarTemporizador() {
        segundosGrabados = 0
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    segundosGrabados++
                    val minutos = segundosGrabados / 60
                    val segundos = segundosGrabados % 60
                    binding.temporizador.text = String.format("%02d:%02d", minutos, segundos)
                }
            }
        }, 1000, 1000)
    }

    private fun detenerTemporizador() {
        timer?.cancel()
        timer = null
    }

    private fun mostrarCargando() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setView(layoutInflater.inflate(R.layout.activity_dialog_loading, null))
        progressDialog = builder.create()
        progressDialog.show()
    }

    private fun ocultarCargando() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun enviarVideoAPI(path: String) {
        val videoFile = File(path)
        val requestFile = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("video", videoFile.name, requestFile)

        mostrarCargando()

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiCliente.instance.procesarVideo(body)
                }
                if (response.isSuccessful && response.body() != null) {
                    guardarArchivosEnCarpeta(response.body()!!)
                    ocultarCargando()
                    Toast.makeText(
                        this@GrabarVideoActivity,
                        "Archivos guardados correctamente",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@GrabarVideoActivity, InicioAppActivity::class.java))
                    finish()
                } else {
                    ocultarCargando()
                    Toast.makeText(
                        this@GrabarVideoActivity,
                        "Error al procesar el video",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                ocultarCargando()
                Toast.makeText(this@GrabarVideoActivity, "Error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private suspend fun guardarArchivosEnCarpeta(data: RespuestaProcesamiento) =
        withContext(Dispatchers.IO) {
            val fecha = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val nombreCarpeta = "ManosQueHablan/Sesion_$fecha"
            val carpeta = File(getExternalFilesDir(null), nombreCarpeta)
            if (!carpeta.exists()) carpeta.mkdirs()

            guardarArchivoLocal(
                ApiCliente.BASE_URL + data.video_url.removePrefix("/"),
                "video.mp4",
                "video/mp4",
                carpeta
            )
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
        url: String,
        nombreArchivo: String,
        mimeType: String,
        carpetaDestino: File
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
                this@GrabarVideoActivity,
                arrayOf(archivoDestino.absolutePath),
                arrayOf(mimeType),
                null
            )

            Log.d("GuardarArchivo", "Guardado: ${archivoDestino.absolutePath}")
        } catch (e: Exception) {
            Log.e("GuardarArchivo", "Error al guardar $nombreArchivo: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
