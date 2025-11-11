package com.frivasm.manosquehablan

import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
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

        // Configurar click listener para "Change Log"
        val cardChangeLog = findViewById<MaterialCardView>(R.id.cardChangeLog)
        cardChangeLog.setOnClickListener {
            mostrarChangeLog()
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
            mostrarWebViewDialog("Política de Privacidad", "https://www.manosquehablan.org/politica-privacidad")
        }

        // Configurar click listener para "Términos de Uso"
        val cardTerminosUso = findViewById<MaterialCardView>(R.id.cardTerminosUso)
        cardTerminosUso.setOnClickListener {
            mostrarWebViewDialog("Términos de Uso", "https://www.manosquehablan.org/terminos-uso")
        }
    }
    
    /**
     * Muestra un diálogo con WebView para mostrar contenido web dentro de la app
     */
    private fun mostrarWebViewDialog(titulo: String, url: String) {
        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.dialog_webview, null)
        dialog.setContentView(view)

        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloWebView)
        val btnCerrar = view.findViewById<ImageView>(R.id.btnCerrarWebView)
        val webView = view.findViewById<WebView>(R.id.webViewContent)

        txtTitulo.text = titulo

        // Configurar WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.webViewClient = WebViewClient()

        // Cargar URL
        webView.loadUrl(url)

        // Configurar botón cerrar
        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
    
    private fun mostrarChangeLog() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.dialog_changelog, null)
        dialog.setContentView(view)

        val btnCerrar = view.findViewById<ImageView>(R.id.btnCerrarChangeLog)
        val txtVersion110 = view.findViewById<TextView>(R.id.txtVersion110)
        val txtVersion103 = view.findViewById<TextView>(R.id.txtVersion103)

        // Animar títulos con colores como los demás diálogos
        animarTituloColores(this, txtVersion110)
        animarTituloColores(this, txtVersion103)

        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // DESHABILITADO - Cancelar animación ya no es necesario
        // val txtTitulo = findViewById<TextView>(R.id.txtTituloConfiguracion)
        // val animador = txtTitulo.tag as? ValueAnimator
        // animador?.cancel()
    }
}
