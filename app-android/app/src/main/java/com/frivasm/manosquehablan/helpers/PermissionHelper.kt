package com.frivasm.manosquehablan.helpers

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: Activity) {
    
    companion object {
        const val REQUEST_CODE_PERMISOS = 101
    }
    
    fun verificarPermisos() {
        val permisos = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // Permisos de medios granulares para Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.READ_MEDIA_VIDEO)
            permisos.add(Manifest.permission.READ_MEDIA_AUDIO)
            permisos.add(Manifest.permission.READ_MEDIA_IMAGES)
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Permisos legacy para versiones anteriores a Android 13
            permisos.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permisos.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val noOtorgados = permisos.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (noOtorgados.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity, noOtorgados.toTypedArray(), REQUEST_CODE_PERMISOS
            )
        }
    }
    
    fun allPermissionsGranted(): Boolean {
        val permisos = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // Permisos de medios granulares para Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.READ_MEDIA_VIDEO)
            permisos.add(Manifest.permission.READ_MEDIA_AUDIO)
            permisos.add(Manifest.permission.READ_MEDIA_IMAGES)
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Permisos legacy para versiones anteriores a Android 13
            permisos.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permisos.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permisos.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        return if (requestCode == REQUEST_CODE_PERMISOS && allPermissionsGranted()) {
            true
        } else {
            false
        }
    }
}
