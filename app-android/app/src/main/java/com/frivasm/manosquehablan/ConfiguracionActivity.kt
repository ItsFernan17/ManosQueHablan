package com.frivasm.manosquehablan

import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.frivasm.manosquehablan.helpers.ConfigHelper
import com.frivasm.manosquehablan.dialogs.DialogUtils

class ConfiguracionActivity : AppCompatActivity() {
    
    private lateinit var preferences: SharedPreferences
    
    // Referencias a los switches
    private lateinit var switchRecordatorio: SwitchMaterial
    private lateinit var switchConfirmacionCompartir: SwitchMaterial
    
    companion object {
        // Claves para SharedPreferences
        const val PREF_MOSTRAR_RECORDATORIO = "mostrar_recordatorio_grabar"
        const val PREF_CONFIRMAR_COMPARTIR_MAL_TRADUCIDOS = "confirmar_compartir_mal_traducidos"
        
        // Valores por defecto
        const val DEFAULT_MOSTRAR_RECORDATORIO = true
        const val DEFAULT_CONFIRMAR_COMPARTIR = true
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)
        
        // Inicializar SharedPreferences
        preferences = getSharedPreferences("manos_que_hablan_config", Context.MODE_PRIVATE)
        
        // Configurar botón de regreso
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        // Configurar animación del título
        val txtTitulo = findViewById<TextView>(R.id.txtTituloConfiguracion)
        animarTituloColores(this, txtTitulo)
        
        // Inicializar switches
        inicializarSwitches()
        
        // Configurar listeners
        configurarListeners()
    }
    
    private fun inicializarSwitches() {
        switchRecordatorio = findViewById(R.id.switchMostrarRecordatorio)
        switchConfirmacionCompartir = findViewById(R.id.switchConfirmarCompartir)

        // Cargar valores guardados
        switchRecordatorio.isChecked = preferences.getBoolean(PREF_MOSTRAR_RECORDATORIO, DEFAULT_MOSTRAR_RECORDATORIO)
        switchConfirmacionCompartir.isChecked = preferences.getBoolean(PREF_CONFIRMAR_COMPARTIR_MAL_TRADUCIDOS, DEFAULT_CONFIRMAR_COMPARTIR)
    }
    
    private fun configurarListeners() {
        switchRecordatorio.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(PREF_MOSTRAR_RECORDATORIO, isChecked).apply()
        }

        switchConfirmacionCompartir.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(PREF_CONFIRMAR_COMPARTIR_MAL_TRADUCIDOS, isChecked).apply()
        }

        // Configurar click listener para "Acerca de"
        val cardAcercaDe = findViewById<MaterialCardView>(R.id.cardAcercaDe)
        cardAcercaDe.setOnClickListener {
            DialogUtils.mostrarDialogoAcercaDe(this)
        }

        // Configurar click listener para "Preguntas Frecuentes"
        val cardPreguntasFrecuentes = findViewById<MaterialCardView>(R.id.cardPreguntasFrecuentes)
        cardPreguntasFrecuentes.setOnClickListener {
            DialogUtils.mostrarDialogoPreguntasFrecuentes(this)
        }

        // Configurar click listener para "Privacidad"
        val cardPrivacidad = findViewById<MaterialCardView>(R.id.cardPrivacidad)
        cardPrivacidad.setOnClickListener {
            DialogUtils.mostrarDialogoPrivacidad(this)
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancelar animación para evitar memory leaks
        val txtTitulo = findViewById<TextView>(R.id.txtTituloConfiguracion)
        val animador = txtTitulo.tag as? ValueAnimator
        animador?.cancel()
    }
}
