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
import com.frivasm.manosquehablan.models.EnlaceSeña
import com.google.gson.Gson
import java.io.IOException
import java.io.InputStreamReader

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
            // Leer el archivo JSON desde assets, manejando posible BOM
            val inputStream = assets.open("catalogo_senias.json")
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val jsonString = reader.readText().removePrefix("\uFEFF")

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
     * Ahora es robusto e inteligente según la cantidad de enlaces
     */
    private fun agregarCategoriaAlLayout(categoria: CategoriaSeña) {
        // Inflar el layout de categoría (usando el existente)
        val inflater = LayoutInflater.from(this)
        val vistaCategoria = inflater.inflate(R.layout.item_categoria_senia, contenedorCategorias, false)
        
        // Configurar textos
        val txtTitulo = vistaCategoria.findViewById<TextView>(R.id.txtTituloCategoria)
        val txtDescripcion = vistaCategoria.findViewById<TextView>(R.id.txtDescripcionCategoria)
        val contenedorCategoria = vistaCategoria.findViewById<LinearLayout>(R.id.contenedorCategoria)
        val contenedorBotones = vistaCategoria.findViewById<LinearLayout>(R.id.contenedorBotones)
        val botonVocabulario = vistaCategoria.findViewById<LinearLayout>(R.id.botonVocabulario)
        val txtBoton = vistaCategoria.findViewById<TextView>(R.id.txtBotonCategoria)
        
        txtTitulo.text = categoria.titulo
        txtDescripcion.text = categoria.descripcion
        
        // Obtener enlaces de forma inteligente
        val enlaces = categoria.obtenerEnlaces()
        
        when {
            enlaces.isEmpty() -> {
                // Sin enlaces - ocultar botones
                contenedorBotones.visibility = android.view.View.GONE
            }
            
            enlaces.size == 1 -> {
                // Un solo enlace - usar el botón existente (mantiene compatibilidad)
                val enlace = enlaces.first()
                txtBoton.text = enlace.texto
                botonVocabulario.visibility = android.view.View.VISIBLE
                
                // Configurar click del botón único
                botonVocabulario.setOnClickListener {
                    abrirEnlace(enlace.url)
                }
                
                // Configurar animación del botón único
                animarBordeBoton(this, botonVocabulario, txtBoton)
                
                // Click en toda la categoría también abre el enlace único
                contenedorCategoria.setOnClickListener {
                    abrirEnlace(enlace.url)
                }
            }
            
            else -> {
                // Múltiples enlaces - crear botones dinámicamente de forma inteligente
                botonVocabulario.visibility = android.view.View.GONE
                crearBotonesMultiples(contenedorBotones, enlaces)
                
                // Sin click en toda la categoría cuando hay múltiples enlaces
                contenedorCategoria.setOnClickListener(null)
                contenedorCategoria.isClickable = false
            }
        }
        
        // Agregar la vista al contenedor principal
        contenedorCategorias.addView(vistaCategoria)
    }
    
    /**
     * Crea botones múltiples de forma inteligente y robusta
     */
    private fun crearBotonesMultiples(contenedor: LinearLayout, enlaces: List<com.frivasm.manosquehablan.models.EnlaceSeña>) {
        val cantidadEnlaces = enlaces.size
        
        // Determinar diseño inteligente según cantidad
        when {
            cantidadEnlaces == 2 -> {
                // 2 enlaces: botones verticales (uno debajo del otro) pero con ancho wrap_content
                contenedor.orientation = LinearLayout.VERTICAL
                contenedor.gravity = android.view.Gravity.CENTER
                enlaces.forEach { enlace ->
                    val boton = crearBotonEnlaceCentrado(enlace)
                    contenedor.addView(boton)
                }
            }
            
            cantidadEnlaces == 3 -> {
                // 3 enlaces: dos filas con distribución inteligente
                contenedor.orientation = LinearLayout.VERTICAL
                
                val primeraFila = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = android.view.Gravity.CENTER
                }
                
                val segundaFila = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = android.view.Gravity.CENTER
                }
                
                // Primera fila: 2 botones
                enlaces.take(2).forEach { enlace ->
                    val boton = crearBotonEnlace(enlace, true)
                    primeraFila.addView(boton)
                }
                
                // Segunda fila: 1 botón
                val boton = crearBotonEnlace(enlaces[2], false)
                segundaFila.addView(boton)
                
                contenedor.addView(primeraFila)
                contenedor.addView(segundaFila)
            }
            
            cantidadEnlaces == 4 -> {
                // 4 enlaces: dos filas con 2 botones cada una
                contenedor.orientation = LinearLayout.VERTICAL
                
                val primeraFila = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = android.view.Gravity.CENTER
                }
                
                val segundaFila = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    gravity = android.view.Gravity.CENTER
                }
                
                // Primera fila: 2 botones
                enlaces.take(2).forEach { enlace ->
                    val boton = crearBotonEnlace(enlace, true)
                    primeraFila.addView(boton)
                }
                
                // Segunda fila: 2 botones
                enlaces.drop(2).forEach { enlace ->
                    val boton = crearBotonEnlace(enlace, true)
                    segundaFila.addView(boton)
                }
                
                contenedor.addView(primeraFila)
                contenedor.addView(segundaFila)
            }
            
            else -> {
                // Más de 4 enlaces: diseño vertical compacto
                contenedor.orientation = LinearLayout.VERTICAL
                enlaces.forEach { enlace ->
                    val boton = crearBotonEnlace(enlace, false)
                    contenedor.addView(boton)
                }
            }
        }
    }
    
    /**
     * Crea un botón individual reutilizando el diseño existente
     */
    private fun crearBotonEnlace(enlace: com.frivasm.manosquehablan.models.EnlaceSeña, usarPeso: Boolean): LinearLayout {
        val boton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@CatalogoSeniasActivity, R.drawable.boton_vocabulario_background)
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            isClickable = true
            isFocusable = true
            // Usar TypedValue para obtener el atributo selectableItemBackground correctamente
            val typedValue = android.util.TypedValue()
            val theme = this@CatalogoSeniasActivity.theme
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
                foreground = ContextCompat.getDrawable(this@CatalogoSeniasActivity, typedValue.resourceId)
            }
            
            layoutParams = if (usarPeso) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                    setMargins(
                        (4 * resources.displayMetrics.density).toInt(),
                        (4 * resources.displayMetrics.density).toInt(),
                        (4 * resources.displayMetrics.density).toInt(),
                        (4 * resources.displayMetrics.density).toInt()
                    )
                }
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        (8 * resources.displayMetrics.density).toInt(),
                        (4 * resources.displayMetrics.density).toInt(),
                        (8 * resources.displayMetrics.density).toInt(),
                        (4 * resources.displayMetrics.density).toInt()
                    )
                }
            }
        }
        
        val txtBoton = TextView(this).apply {
            text = enlace.texto
            textSize = if (usarPeso) 13f else 14f
            setTextColor(ContextCompat.getColor(this@CatalogoSeniasActivity, R.color.accent_color))
            typeface = resources.getFont(R.font.poppins_semibold)
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        boton.addView(txtBoton)
        
        // Configurar click
        boton.setOnClickListener {
            abrirEnlace(enlace.url)
        }
        
        // Aplicar animación
        animarBordeBoton(this, boton, txtBoton)
        
        return boton
    }
    
    /**
     * Crea un botón centrado con el mismo tamaño que el botón original
     */
    private fun crearBotonEnlaceCentrado(enlace: com.frivasm.manosquehablan.models.EnlaceSeña): LinearLayout {
        val boton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@CatalogoSeniasActivity, R.drawable.boton_vocabulario_background)
            setPadding(
                (16 * resources.displayMetrics.density).toInt(), // Mismo padding que el botón original
                (8 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            isClickable = true
            isFocusable = true
            // Usar TypedValue para obtener el atributo selectableItemBackground correctamente
            val typedValue = android.util.TypedValue()
            val theme = this@CatalogoSeniasActivity.theme
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
                foreground = ContextCompat.getDrawable(this@CatalogoSeniasActivity, typedValue.resourceId)
            }
            
            // wrap_content para mantener el tamaño natural del botón
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(
                    0,
                    (4 * resources.displayMetrics.density).toInt(),
                    0,
                    (4 * resources.displayMetrics.density).toInt()
                )
            }
        }
        
        val txtBoton = TextView(this).apply {
            text = enlace.texto
            textSize = 14f // Mismo tamaño que el botón original
            setTextColor(ContextCompat.getColor(this@CatalogoSeniasActivity, R.color.accent_color))
            typeface = resources.getFont(R.font.poppins_semibold)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        boton.addView(txtBoton)
        
        // Configurar click
        boton.setOnClickListener {
            abrirEnlace(enlace.url)
        }
        
        // Aplicar animación
        animarBordeBoton(this, boton, txtBoton)
        
        return boton
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
