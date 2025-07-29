package com.frivasm.manosquehablan

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

@Suppress("DEPRECATION")
class InicioSplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_splash_screen)
        Handler().postDelayed({
            val intent = Intent(this,InicioAppActivity::class.java)
            startActivity(intent)
            finish()
        },3000)
    }
}