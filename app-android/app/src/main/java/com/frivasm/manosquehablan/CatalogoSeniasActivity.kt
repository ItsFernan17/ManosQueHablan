package com.frivasm.manosquehablan

import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.frivasm.manosquehablan.models.CategoriaSeña
import com.frivasm.manosquehablan.models.CatalogoSenias
import com.google.gson.Gson
import java.io.IOException

class CatalogoSeniasActivity : AppCompatActivity() {
    
    private lateinit var contenedorCategorias: LinearLayout
    
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
        
        // Obtener contenedor donde se agregarán las categorías dinámicamente
        contenedorCategorias = findViewById(R.id.contenedorCategorias)
        
        // Cargar categorías desde JSON
        cargarCategoriasDesdeJSON()
    }
    
    
    /**
     * Carga las categorías desde el archivo JSON y las agrega dinámicamente al layout
     */
    private fun cargarCategoriasDesdeJSON() {
        try {
            // Leer el archivo JSON desde assets
            val jsonString = assets.open("catalogo_senias.json").bufferedReader().use { it.readText() }
            
            // Parsear JSON usando Gson
            val gson = Gson()
            val catalogoSenias = gson.fromJson(jsonString, CatalogoSenias::class.java)
            
            // Agregar cada categoría dinámicamente
            catalogoSenias.categorias.forEach { categoria ->
                agregarCategoriaAlLayout(categoria)
            }
            
        } catch (e: IOException) {
            Log.e("CatalogoSenias", "Error al cargar categorías desde JSON", e)
            // En caso de error, mostrar mensaje al usuario
            mostrarErrorCarga()
        } catch (e: Exception) {
            Log.e("CatalogoSenias", "Error al parsear JSON de categorías", e)
            mostrarErrorCarga()
        }
    }
    
    /**
     * Agrega una categoría individual al layout usando el diseño existente
     */
    private fun agregarCategoriaAlLayout(categoria: CategoriaSeña) {
        // Inflar el layout de categoría (vamos a crear este layout)
        val inflater = LayoutInflater.from(this)
        val vistaCategoria = inflater.inflate(R.layout.item_categoria_senia, contenedorCategorias, false)
        
        // Configurar textos
        val txtTitulo = vistaCategoria.findViewById<TextView>(R.id.txtTituloCategoria)
        val txtDescripcion = vistaCategoria.findViewById<TextView>(R.id.txtDescripcionCategoria)
        val txtBoton = vistaCategoria.findViewById<TextView>(R.id.txtBotonCategoria)
        val contenedorCategoria = vistaCategoria.findViewById<LinearLayout>(R.id.contenedorCategoria)
        val botonVocabulario = vistaCategoria.findViewById<LinearLayout>(R.id.botonVocabulario)
        
        txtTitulo.text = categoria.titulo
        txtDescripcion.text = categoria.descripcion
        txtBoton.text = categoria.boton_texto
        
        // Configurar click para abrir enlace
        contenedorCategoria.setOnClickListener {
            abrirEnlace(categoria.enlace)
        }
        
        // Configurar animación del botón
        animarBordeBoton(this, botonVocabulario, txtBoton)
        
        // Agregar la vista al contenedor principal
        contenedorCategorias.addView(vistaCategoria)
    }
    
    /**
     * Muestra un mensaje de error si no se pueden cargar las categorías
     */
    private fun mostrarErrorCarga() {
        val txtError = TextView(this)
        txtError.text = "Error al cargar categorías. Inténtalo más tarde."
        txtError.textSize = 16f
        txtError.setTextColor(ContextCompat.getColor(this, R.color.texto_secundario))
        txtError.gravity = android.view.Gravity.CENTER
        txtError.setPadding(32, 16, 32, 16)
        contenedorCategorias.addView(txtError)
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
