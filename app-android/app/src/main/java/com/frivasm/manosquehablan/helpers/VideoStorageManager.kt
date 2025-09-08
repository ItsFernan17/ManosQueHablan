package com.frivasm.manosquehablan.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestor de Almacenamiento de Videos para Manos Que Hablan
 * 
 * Estrategia de Privacidad Total:
 * - Guarda TODOS los archivos en carpeta privada de la app (máxima seguridad)
 * - NO los registra en MediaStore (no aparecen en galería)
 * - Videos solo visibles dentro de la aplicación
 * - Privacidad y seguridad garantizadas
 * - Compatible con todos los niveles de API
 */
class VideoStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "VideoStorageManager"
        private const val APP_FOLDER_NAME = "ManosQueHablan"
        private const val AUTHORITY = "com.frivasm.manosquehablan.fileprovider"
    }

    /**
     * Información de archivo guardado (solo privado, sin referencia en galería)
     */
    data class SavedFileInfo(
        val privateFile: File,           // Archivo real en carpeta privada
        val galleryUri: Uri?,           // Siempre null - no hay registro en galería
        val displayName: String,        // Nombre del archivo
        val mimeType: String           // Tipo MIME
    )

    /**
     * Guarda un archivo desde InputStream en carpeta privada y lo registra en galería
     */
    suspend fun saveFileWithGalleryReference(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        sessionFolder: String
    ): SavedFileInfo = withContext(Dispatchers.IO) {
        
        // 1. Crear carpeta privada de la sesión
        val privateDir = File(context.filesDir, "$APP_FOLDER_NAME/$sessionFolder")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }
        
        // 2. Guardar archivo en carpeta privada
        val privateFile = File(privateDir, fileName)
        Log.d(TAG, "Guardando archivo privado: ${privateFile.absolutePath}")
        
        inputStream.use { input ->
            privateFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // 3. Verificar que se guardó correctamente
        if (!privateFile.exists() || privateFile.length() == 0L) {
            throw Exception("Error: archivo guardado está vacío o corrupto")
        }
        
        Log.d(TAG, "Archivo privado guardado: ${privateFile.length()} bytes")
        
        // 4. NO registrar en galería - mantener videos privados en la app únicamente
        // Los videos permanecen seguros y solo visibles dentro de la aplicación
        val galleryUri: Uri? = null
        
        SavedFileInfo(
            privateFile = privateFile,
            galleryUri = galleryUri,
            displayName = fileName,
            mimeType = mimeType
        )
    }

    /**
     * [DESHABILITADO] Registra un video en MediaStore para que aparezca en galería
     * Función mantenida para referencia futura si se requiere habilitar galería
     */
    private suspend fun registerVideoInGallery(
        privateFile: File,
        displayName: String,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        // FUNCIÓN DESHABILITADA - Videos permanecen privados en la app
        return@withContext null
        
        /* CÓDIGO ORIGINAL COMENTADO PARA REFERENCIA FUTURA
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Usar MediaStore con referencias
                registerVideoInMediaStore(privateFile, displayName, mimeType)
            } else {
                // Android 9 y anteriores - Usar symlink/scan tradicional
                registerVideoLegacy(privateFile, displayName, mimeType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar en galería (continuando): ${e.message}")
            // Continuar sin error - el archivo sigue en carpeta privada
            null
        }
        */
    }

    /**
     * Registro en MediaStore para Android 10+
     */
    private fun registerVideoInMediaStore(
        privateFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.TITLE, displayName.substringBeforeLast('.'))
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.SIZE, privateFile.length())
                
                // Marcar como pendiente para que no aparezca hasta que esté listo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$APP_FOLDER_NAME")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                // Copiar contenido del archivo privado al MediaStore
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(privateFile).use { input ->
                        input.copyTo(output)
                    }
                }
                
                // Marcar como completado
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, updateValues, null, null)
                }
                
                Log.d(TAG, "Video registrado en MediaStore: $uri")
                uri
            } else {
                Log.w(TAG, "No se pudo crear entrada en MediaStore")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando en MediaStore: ${e.message}")
            null
        }
    }

    /**
     * Registro legacy para Android 9 y anteriores
     */
    private fun registerVideoLegacy(
        privateFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            // Crear carpeta en Movies si no existe
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                APP_FOLDER_NAME
            )
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            
            // Crear archivo de referencia en la galería
            val galleryFile = File(moviesDir, displayName)
            
            // Copiar archivo a ubicación pública para galería
            FileInputStream(privateFile).use { input ->
                galleryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Escanear el archivo para que aparezca en galería
            scanFileTraditional(galleryFile, mimeType)
            
            // Retornar URI del archivo público
            FileProvider.getUriForFile(context, AUTHORITY, galleryFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error en registro legacy: ${e.message}")
            null
        }
    }

    /**
     * [DESHABILITADO] Escaneo tradicional de archivos
     * Función mantenida para referencia futura si se requiere visibilidad en galería
     */
    private fun scanFileTraditional(file: File, mimeType: String) {
        // FUNCIÓN DESHABILITADA - Archivos permanecen completamente privados
        Log.d(TAG, "Escaneo deshabilitado - archivo privado: ${file.absolutePath}")
        
        /* CÓDIGO ORIGINAL COMENTADO PARA REFERENCIA FUTURA
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            { path, uri ->
                Log.d(TAG, "Archivo escaneado: $path -> $uri")
            }
        )
        */
    }

    /**
     * Obtiene la carpeta privada de una sesión
     */
    fun getSessionPrivateDir(sessionName: String): File {
        return File(context.filesDir, "$APP_FOLDER_NAME/$sessionName")
    }

    /**
     * Lista todos los videos guardados en sesiones
     */
    fun getAllSavedVideos(): List<File> {
        val appDir = File(context.filesDir, APP_FOLDER_NAME)
        if (!appDir.exists()) return emptyList()
        
        val videos = mutableListOf<File>()
        
        appDir.listFiles()?.forEach { sessionDir ->
            if (sessionDir.isDirectory) {
                sessionDir.listFiles { _, name -> 
                    name.endsWith(".mp4", true) 
                }?.let { videos.addAll(it) }
            }
        }
        
        return videos
    }

    /**
     * Elimina un video y su referencia en galería
     */
    suspend fun deleteVideo(privateFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // Eliminar archivo privado
            val deleted = privateFile.delete()
            
            // Intentar eliminar referencia en galería si existe
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Buscar y eliminar entrada en MediaStore
                val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(privateFile.name)
                
                context.contentResolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    selection,
                    selectionArgs
                )
            }
            
            Log.d(TAG, "Video eliminado: ${privateFile.name}")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando video: ${e.message}")
            false
        }
    }
    
    /**
     * Obtiene el URI de galería para un archivo privado, si existe
     */
    suspend fun getGalleryUriForFile(privateFile: File): Uri? = withContext(Dispatchers.IO) {
        try {
            val fileName = privateFile.name
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME
            )
            
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    Log.d(TAG, "URI de galería encontrado: $uri para archivo: $fileName")
                    return@withContext uri
                }
            }
            
            Log.w(TAG, "No se encontró URI de galería para: $fileName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo URI de galería: ${e.message}")
            null
        }
    }
}
