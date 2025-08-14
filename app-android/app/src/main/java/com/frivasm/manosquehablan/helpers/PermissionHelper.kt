package com.frivasm.manosquehablan.helpers

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val activity: Activity) {
    
    companion object {
        const val REQUEST_CODE_PERMISOS = 101
    }
    
    fun verificarPermisos() {
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
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (noOtorgados.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity, noOtorgados.toTypedArray(), REQUEST_CODE_PERMISOS
            )
        }
    }
    
    fun allPermissionsGranted(): Boolean {
        val permisos = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        return permisos.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        return if (requestCode == REQUEST_CODE_PERMISOS && allPermissionsGranted()) {
            true
        } else {
            Toast.makeText(activity, "Permisos requeridos para continuar", Toast.LENGTH_LONG).show()
            false
        }
    }
}
