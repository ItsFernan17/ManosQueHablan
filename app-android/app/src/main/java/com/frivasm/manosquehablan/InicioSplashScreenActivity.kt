package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

@ExperimentalCamera2Interop
class InicioSplashScreenActivity : AppCompatActivity() {

    private var navigationHandler: Handler? = null
    private val navigationRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            val intent = Intent(this, InicioAppActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_splash_screen)

        // Navegar a la actividad principal después de 2 segundos
        navigationHandler = Handler(Looper.getMainLooper())
        navigationHandler?.postDelayed(navigationRunnable, 2000) // 2 segundos de splash simple
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar el handler para prevenir memory leaks
        navigationHandler?.removeCallbacks(navigationRunnable)
        navigationHandler = null
    }
}
