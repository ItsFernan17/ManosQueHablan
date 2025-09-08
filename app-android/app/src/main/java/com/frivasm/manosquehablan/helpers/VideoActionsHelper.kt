package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.FileProvider
import com.frivasm.manosquehablan.dialogs.DialogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@ExperimentalCamera2Interop
object VideoActionsHelper {

    fun compartirVideo(context: Context, video: File) {
        if (!video.exists()) {
            Toast.makeText(context, "Video no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        // Verificar si el video está mal traducido y si se debe confirmar
        val esVideoMalTraducido = VideoTranslationStatusHelper.esVideoMalTraducido(video)
        val debeConfirmar = ConfigHelper.debeConfirmarCompartirMalTraducidos(context)
        
        if (esVideoMalTraducido && debeConfirmar) {
            // Mostrar diálogo de confirmación
            mostrarDialogoConfirmacionCompartir(context, video)
        } else {
            // Compartir directamente
            ejecutarCompartirVideo(context, video)
        }
    }
    
    /**
     * Muestra diálogo de confirmación para compartir videos mal traducidos
     */
    private fun mostrarDialogoConfirmacionCompartir(context: Context, video: File) {
        DialogUtils.mostrarDialogoConfirmarCompartirMalTraducido(
            context = context,
            onConfirmar = {
                ejecutarCompartirVideo(context, video)
            },
            onCancelar = {
                // No hacer nada, solo cerrar el diálogo
            }
        )
    }
    
    /**
     * Ejecuta el compartir video sin verificaciones adicionales
     */
    private fun ejecutarCompartirVideo(context: Context, video: File) {
        // Usar corrutina para obtener URI de galería
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val storageManager = VideoStorageManager(context)
                val galleryUri = storageManager.getGalleryUriForFile(video)
                
                if (galleryUri != null) {
                    // Estrategia 1: Usar URI de galería (preferido)
                    Log.d("VideoActionsHelper", "Compartiendo usando URI de galería: $galleryUri")
                    compartirConUri(context, galleryUri, "URI de galería")
                } else {
                    // Estrategia 2: Fallback a FileProvider
                    Log.d("VideoActionsHelper", "URI de galería no encontrado, usando FileProvider")
                    compartirConFileProvider(context, video)
                }
                
            } catch (e: Exception) {
                Log.e("VideoActionsHelper", "Error obteniendo URI de galería para compartir: ${e.message}")
                // Fallback a FileProvider
                compartirConFileProvider(context, video)
            }
        }
    }
    
    /**
     * Comparte video usando URI de galería
     */
    private fun compartirConUri(context: Context, uri: Uri, source: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Video compartido desde Manos Que Hablan")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooser = Intent.createChooser(intent, "Compartir video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
                Log.d("VideoActionsHelper", "Video compartido exitosamente usando $source")
            } else {
                Toast.makeText(context, "No hay aplicación para compartir", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("VideoActionsHelper", "Error compartiendo con $source: ${e.message}")
            Toast.makeText(context, "Error al compartir video", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Comparte video usando FileProvider (fallback)
     */
    private fun compartirConFileProvider(context: Context, video: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TEXT, "Video compartido desde Manos Que Hablan")
            }
            
            val chooser = Intent.createChooser(intent, "Compartir video")
            
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "No hay aplicación para compartir", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("VideoActionsHelper", "Error compartiendo video: ${e.message}")
            Toast.makeText(context, "Error al compartir video", Toast.LENGTH_SHORT).show()
        }
    }

    fun reproducirVideo(context: Context, video: File) {
        if (!video.exists()) {
            Toast.makeText(context, "Video no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        // Usar corrutina para obtener URI de galería de forma asíncrona
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val storageManager = VideoStorageManager(context)
                val galleryUri = storageManager.getGalleryUriForFile(video)
                
                if (galleryUri != null) {
                    // Estrategia 1: Usar URI de galería (preferido)
                    Log.d("VideoActionsHelper", "Usando URI de galería: $galleryUri para archivo: ${video.name}")
                    reproducirConUri(context, galleryUri, "URI de galería")
                } else {
                    // Estrategia 2: Fallback a FileProvider para archivos privados
                    Log.d("VideoActionsHelper", "URI de galería no encontrado, usando FileProvider para: ${video.name}")
                    reproducirConFileProvider(context, video)
                }
                
            } catch (e: Exception) {
                Log.e("VideoActionsHelper", "Error obteniendo URI de galería: ${e.message}")
                // Fallback a FileProvider
                reproducirConFileProvider(context, video)
            }
        }
    }
    
    /**
     * Reproduce video usando URI de galería (MediaStore)
     */
    private fun reproducirConUri(context: Context, uri: Uri, source: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooser = Intent.createChooser(intent, "Reproducir video con:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val packageManager = context.packageManager
            if (chooser.resolveActivity(packageManager) != null) {
                context.startActivity(chooser)
                Log.d("VideoActionsHelper", "Video abierto exitosamente usando $source")
            } else {
                // Fallback si no hay apps disponibles
                val directIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (directIntent.resolveActivity(packageManager) != null) {
                    context.startActivity(directIntent)
                } else {
                    Toast.makeText(context, "Instala un reproductor de video (VLC, MX Player, etc.) para reproducir videos", Toast.LENGTH_LONG).show()
                }
            }
            
        } catch (e: Exception) {
            Log.e("VideoActionsHelper", "Error reproduciendo con $source: ${e.message}")
            Toast.makeText(context, "Error al reproducir video", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Reproduce video usando FileProvider (fallback)
     */
    private fun reproducirConFileProvider(context: Context, video: File) {
        try {
            // Generar URI segura con FileProvider
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
            Log.d("VideoActionsHelper", "URI FileProvider generada: $uri para archivo: ${video.absolutePath}")
            
            // Crear intent con configuración robusta
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) // Por si acaso
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Siempre usar chooser para máxima compatibilidad
            val chooser = Intent.createChooser(intent, "Seleccionar reproductor de video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Verificar que hay apps disponibles antes de lanzar
            val packageManager = context.packageManager
            if (chooser.resolveActivity(packageManager) != null) {
                context.startActivity(chooser)
                Log.d("VideoActionsHelper", "Lanzando reproductor FileProvider para: ${video.name}")
            } else {
                // Fallback: intentar con intent directo
                Log.w("VideoActionsHelper", "No se encontró chooser, intentando directo")
                val directIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (directIntent.resolveActivity(packageManager) != null) {
                    context.startActivity(directIntent)
                } else {
                    Toast.makeText(context, "Instala un reproductor de video (VLC, MX Player, etc.) para reproducir videos", Toast.LENGTH_LONG).show()
                }
            }
            
        } catch (e: Exception) {
            Log.e("VideoActionsHelper", "Error abriendo reproductor: ${e.message}")
            Toast.makeText(context, "Error al reproducir video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun abrirCarpetaVideos(context: Context, video: File) {
        try {
            val carpeta = video.parentFile ?: return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", carpeta)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Busca la carpeta 'ManosQueHablan' en tu Galería", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Busca la carpeta 'ManosQueHablan' en tu Galería", Toast.LENGTH_LONG).show()
        }
    }

    fun eliminarVideoConConfirmacion(
        context: Context,
        archivo: File,
        vista: android.view.View? = null,
        onEliminado: () -> Unit
    ) {
        com.frivasm.manosquehablan.dialogs.DialogUtils.mostrarDialogoEliminar(context, archivo) {
            // Llamar al callback proporcionado
            onEliminado()
            
            // Si el contexto es una actividad y tenemos la vista, animar la eliminación
            if (context is com.frivasm.manosquehablan.InicioAppActivity && vista != null) {
                context.runOnUiThread {
                    context.eliminarVideoConAnimacion(vista, archivo)
                }
            } else if (context is com.frivasm.manosquehablan.InicioAppActivity) {
                // Si no tenemos la vista, recargar normalmente
                context.runOnUiThread {
                    context.aplicarOrdenamiento(com.frivasm.manosquehablan.helpers.PreferenciasHelper.obtenerOrden(context))
                }
            }
        }
    }
}
