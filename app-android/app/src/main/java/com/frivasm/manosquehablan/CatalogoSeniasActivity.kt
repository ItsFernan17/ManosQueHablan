package com.frivasm.manosquehablan

import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class CatalogoSeniasActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalogo_senias)
        
        // Configurar botón de regreso
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
        
        // Configurar animación del título
        val txtTitulo = findViewById<TextView>(R.id.txtTituloCatalogo)
        animarTituloColores(this, txtTitulo)
        
        // Configurar animación del botón de vocabulario
        val botonVocabulario = findViewById<LinearLayout>(R.id.botonVocabulario)
        val txtBotonVocabulario = findViewById<TextView>(R.id.txtBotonVocabulario)
        animarBordeBoton(this, botonVocabulario, txtBotonVocabulario)
        
        // Configurar enlaces a recursos externos
        configurarEnlaces()
    }
    
    private fun configurarEnlaces() {
        // Configurar enlaces de YouTube/TikTok para cada categoría
        val enlaceFrasesCortesia = findViewById<LinearLayout>(R.id.enlaceFrasesCortesia)
        enlaceFrasesCortesia.setOnClickListener {
            abrirEnlace("https://youtube.com/watch?v=ejemplo_frases_cortesia")
        }
        
        // Aquí se pueden agregar más categorías y enlaces
    }
    
    private fun abrirEnlace(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Manejar error si no se puede abrir el enlace
        }
    }
    
    private fun animarTituloColores(context: Context, textView: TextView) {
        val colorRojo = ContextCompat.getColor(context, R.color.rojo)
        val colorCeleste = ContextCompat.getColor(context, R.color.celeste)
        
        val animador = ValueAnimator.ofFloat(0f, 1f)
        animador.duration = 3000 // Un poco más rápido pero aún suave
        animador.repeatCount = ValueAnimator.INFINITE
        animador.repeatMode = ValueAnimator.REVERSE
        animador.interpolator = AccelerateDecelerateInterpolator() // Interpolador suave
        
        animador.addUpdateListener { animation ->
            val progreso = animation.animatedValue as Float
            val color = ArgbEvaluator().evaluate(progreso, colorRojo, colorCeleste) as Int
            textView.setTextColor(color)
        }
        
        // Guardar referencia del animador en el tag de la vista para poder cancelarlo después
        textView.tag = animador
        animador.start()
    }
    
    private fun animarBordeBoton(context: Context, linearLayout: LinearLayout, textView: TextView) {
        val colorRojo = ContextCompat.getColor(context, R.color.rojo)
        val colorCeleste = ContextCompat.getColor(context, R.color.celeste)
        
        val animador = ValueAnimator.ofFloat(0f, 1f)
        animador.duration = 3000 // Misma duración que el título
        animador.repeatCount = ValueAnimator.INFINITE
        animador.repeatMode = ValueAnimator.REVERSE
        animador.interpolator = AccelerateDecelerateInterpolator()
        
        animador.addUpdateListener { animation ->
            val progreso = animation.animatedValue as Float
            val color = ArgbEvaluator().evaluate(progreso, colorRojo, colorCeleste) as Int
            
            // Crear un nuevo GradientDrawable con el color animado para el borde
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.cornerRadius = 12f * context.resources.displayMetrics.density // 12dp en pixels
            drawable.setColor(ContextCompat.getColor(context, android.R.color.white))
            drawable.setStroke((2 * context.resources.displayMetrics.density).toInt(), color) // 2dp en pixels
            
            linearLayout.background = drawable
            
            // Animar también el color del texto
            textView.setTextColor(color)
        }
        
        // Guardar referencia del animador en el tag de la vista para poder cancelarlo después
        linearLayout.tag = animador
        animador.start()
    }
}
