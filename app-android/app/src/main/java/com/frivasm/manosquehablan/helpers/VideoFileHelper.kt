package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.content.Intent

class VideoFileHelper(
    private val context: Context
) {
    
    fun enviarVideoAPI(path: String) {
        // En lugar de mostrar un dialog, navegar a la nueva Activity
        val intent = Intent(context, com.frivasm.manosquehablan.ProcesandoVideoActivity::class.java)
        intent.putExtra("VIDEO_PATH", path)
        context.startActivity(intent)
        
        // Si estamos en GrabarVideoActivity, cerrarla
        if (context is android.app.Activity) {
            context.finish()
        }
    }
}
