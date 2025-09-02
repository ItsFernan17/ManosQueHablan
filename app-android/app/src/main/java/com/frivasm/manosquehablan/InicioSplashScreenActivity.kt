package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class InicioSplashScreenActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_splash_screen)
        
        // Navegar a la actividad principal después de 2 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, InicioAppActivity::class.java)
            startActivity(intent)
            finish()
        }, 2000) // 2 segundos de splash simple
    }
}
