package com.frivasm.manosquehablan

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.frivasm.manosquehablan.helpers.VideoViewBuilder
import com.frivasm.manosquehablan.helpers.VideoStorageManager
import com.frivasm.manosquehablan.utils.VideoUtils
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SearchVideoActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var editTextSearch: EditText
    private lateinit var iconClear: ImageView
    private lateinit var iconSearch: ImageView
    private lateinit var contenedorResultados: LinearLayout
    private lateinit var vistaSinResultados: LinearLayout
    private lateinit var txtSinResultados: TextView
    private lateinit var txtConteoResultados: TextView
    private lateinit var contenedorContador: LinearLayout
    
    private var searchJob: Job? = null
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_video)
        
        initViews()
        setupListeners()
        setupSearch()
        
        // Mostrar estado inicial
        mostrarEstadoInicial()
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        editTextSearch = findViewById(R.id.editTextSearch)
        iconClear = findViewById(R.id.iconClear)
        iconSearch = findViewById(R.id.iconSearch)
        contenedorResultados = findViewById(R.id.contenedorResultados)
        vistaSinResultados = findViewById(R.id.vistaSinResultados)
        txtSinResultados = findViewById(R.id.txtSinResultados)
        txtConteoResultados = findViewById(R.id.txtConteoResultados)
        contenedorContador = findViewById(R.id.contenedorContador)
    }
    
    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        iconClear.setOnClickListener {
            // Animación de "borrado" creativa
            iconClear.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    editTextSearch.setText("")
                    iconClear.visibility = View.GONE
                    iconSearch.visibility = View.VISIBLE
                    
                    iconClear.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }
    
    private fun setupSearch() {
        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                buscarVideosEnTiempoReal(query)
                
                // Mostrar/ocultar botón de limpiar
                if (query.isNotEmpty()) {
                    iconClear.visibility = View.VISIBLE
                    iconSearch.visibility = View.GONE
                    animarIconoBusqueda()
                } else {
                    iconClear.visibility = View.GONE
                    iconSearch.visibility = View.VISIBLE
                }
            }
        })
        
        // Enfocar automáticamente el campo de búsqueda
        editTextSearch.requestFocus()
        
        // Placeholder profesional y directo
        editTextSearch.hint = "Nombre del video"
        
        // Mostrar teclado automáticamente
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        editTextSearch.postDelayed({
            imm.showSoftInput(editTextSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }
    
    private fun animarIconoBusqueda() {
        iconClear.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .withEndAction {
                iconClear.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }
    
    private fun buscarVideosEnTiempoReal(query: String) {
        // Cancelar búsqueda anterior
        searchJob?.cancel()
        
        if (query.isEmpty()) {
            mostrarEstadoInicial()
            return
        }
        
        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resultados = buscarVideos(query)
                
                withContext(Dispatchers.Main) {
                    if (isActive) { // Verificar que la corrutina no fue cancelada
                        mostrarResultados(resultados, query)
                    }
                }
            } catch (e: CancellationException) {
                // Búsqueda cancelada, no hacer nada
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isActive) {
                        mostrarError()
                    }
                }
            }
        }
    }
    
    private suspend fun buscarVideos(query: String): List<File> {
        return withContext(Dispatchers.IO) {
            // Cargar desde ambos sistemas: viejo (externo) y nuevo (privado)
            val videosLegacy = cargarVideosLegacy()
            val videosNuevos = cargarVideosPrivados()

            val todosLosVideos = (videosLegacy + videosNuevos)
                .distinctBy { it.absolutePath } // Evitar duplicados
                .filter { it.length() > 100_000 } // Filtro de tamaño consistente

            // Normalizar la query para búsqueda más robusta
            val queryNormalizada = normalizarTexto(query)

            // Filtrar por nombre de archivo y carpeta (que representa la palabra/seña)
            todosLosVideos.filter { video ->
                val nombreCarpeta = video.parentFile?.name ?: ""
                val nombreArchivo = video.nameWithoutExtension

                // Normalizar los textos a comparar
                val carpetaNormalizada = normalizarTexto(nombreCarpeta)
                val archivoNormalizado = normalizarTexto(nombreArchivo)

                // Búsqueda flexible y robusta
                coincideBusquedaFlexible(queryNormalizada, carpetaNormalizada, archivoNormalizado)
            }.sortedByDescending { it.lastModified() } // Ordenar por más reciente
        }
    }
    
    private fun mostrarEstadoInicial() {
        contenedorResultados.removeAllViews()
        vistaSinResultados.visibility = View.VISIBLE
        contenedorContador.visibility = View.GONE
        
        txtSinResultados.text = "Ingrese el nombre del video para encontrarlo"
    }
    
    private fun mostrarResultados(videos: List<File>, query: String) {
        contenedorResultados.removeAllViews()
        
        if (videos.isEmpty()) {
            vistaSinResultados.visibility = View.VISIBLE
            contenedorContador.visibility = View.GONE
            txtSinResultados.text = "No se encontraron videos con el nombre \"$query\""
            return
        }
        
        vistaSinResultados.visibility = View.GONE
        contenedorContador.visibility = View.VISIBLE
        
        // Texto simple para el contador
        val textoContador = if (videos.size == 1) {
            "1 video encontrado"
        } else {
            "${videos.size} videos encontrados"
        }
        txtConteoResultados.text = textoContador
        
        // Animación del contenedor contador
        contenedorContador.alpha = 0f
        contenedorContador.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        // Agrupar videos por fecha (usando la misma lógica que Vista Fecha)
        val videosPorFecha = videos.groupBy { video ->
            val fecha = Date(video.lastModified())
            formatoFecha.format(fecha)
        }
        
        // Ordenar fechas de más reciente a más antigua
        val fechasOrdenadas = videosPorFecha.keys.sortedByDescending { fechaStr ->
            try {
                formatoFecha.parse(fechaStr)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        
        // Animación de entrada para los resultados
        var delayCounter = 0
        var videoIndex = 0
        val totalVideos = videos.size
        
        for (fechaStr in fechasOrdenadas) {
            val videosDelDia = videosPorFecha[fechaStr] ?: continue
            
            // Crear encabezado de fecha
            val encabezadoFecha = crearEncabezadoFecha(fechaStr)
            contenedorResultados.addView(encabezadoFecha)
            
            // Animación de entrada para el encabezado
            encabezadoFecha.alpha = 0f
            encabezadoFecha.translationY = 20f
            encabezadoFecha.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(delayCounter * 50L)
                .start()
            delayCounter++
            
            // Agregar videos del día
            for (video in videosDelDia) {
                val vistaVideo = layoutInflater.inflate(R.layout.item_video_por_fecha, null)
                
                // Construir la vista del video usando el helper existente
                VideoViewBuilder.construirVistaVideoPorFecha(
                    context = this,
                    vista = vistaVideo,
                    video = video,
                    formatoFecha = formatoFecha,
                    onRecargar = { videoActualizado ->
                        // Recargar búsqueda para actualizar resultados
                        val queryActual = editTextSearch.text.toString().trim()
                        if (queryActual.isNotEmpty()) {
                            buscarVideosEnTiempoReal(queryActual)
                        }
                    },
                    mostrarEncabezadoFecha = false // No mostrar encabezado individual
                )
                
                // Controlar línea divisoria: solo mostrar si hay más de 1 video Y no es el último
                val lineaDivisoria = vistaVideo.findViewById<View>(R.id.lineaDivisoria)
                if (totalVideos == 1 || videoIndex == totalVideos - 1) {
                    lineaDivisoria.visibility = View.GONE
                } else {
                    lineaDivisoria.visibility = View.VISIBLE
                }
                videoIndex++
                
                contenedorResultados.addView(vistaVideo)
                
                // Animación de entrada para cada video
                vistaVideo.alpha = 0f
                vistaVideo.translationY = 20f
                vistaVideo.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(delayCounter * 50L)
                    .start()
                delayCounter++
            }
        }
    }
    
    private fun crearEncabezadoFecha(fechaStr: String): View {
        val encabezado = layoutInflater.inflate(R.layout.item_encabezado_fecha, null)
        val txtFecha = encabezado.findViewById<TextView>(R.id.txtEncabezadoFecha)
        
        // Mostrar la fecha directamente sin conversiones a "Hoy" o "Ayer"
        val fechaAmigable = try {
            val fecha = formatoFecha.parse(fechaStr)
            if (fecha != null) {
                val formato = SimpleDateFormat("d 'de' MMMM", Locale("es", "GT"))
                formato.format(fecha)
            } else {
                fechaStr
            }
        } catch (e: Exception) {
            fechaStr
        }
        
        txtFecha.text = fechaAmigable
        return encabezado
    }
    
    private fun mostrarError() {
        contenedorResultados.removeAllViews()
        vistaSinResultados.visibility = View.VISIBLE
        contenedorContador.visibility = View.GONE
        txtSinResultados.text = "Error durante la búsqueda. Intente nuevamente"
    }
    
    /**
     * Carga videos del sistema legacy (external files)
     */
    private fun cargarVideosLegacy(): List<File> {
        val raiz = File(getExternalFilesDir(null), "ManosQueHablan")
        if (!raiz.exists() || !raiz.isDirectory) {
            return emptyList()
        }

        return raiz.listFiles()?.filter { it.isDirectory }?.flatMap { carpeta ->
            carpeta.listFiles()?.filter {
                it.isFile && 
                it.extension.equals("mp4", ignoreCase = true) &&
                it.exists() && // Verificar que el archivo aún existe
                it.length() > 100_000 // Filtro de tamaño consistente
            } ?: emptyList()
        } ?: emptyList()
    }

    /**
     * Carga videos del nuevo sistema (private files)
     */
    private fun cargarVideosPrivados(): List<File> {
        return try {
            val storageManager = VideoStorageManager(this)
            // Filtrar solo archivos que aún existen
            storageManager.getAllSavedVideos().filter { it.exists() && it.length() > 100_000 }
        } catch (e: Exception) {
            android.util.Log.w("SearchVideoActivity", "Error cargando videos privados: ${e.message}")
            emptyList()
        }
    }

    /**
     * Normaliza el texto para búsqueda más robusta
     * - Convierte a minúsculas
     * - Quita acentos y caracteres especiales
     * - Hace la búsqueda más flexible
     */
    private fun normalizarTexto(texto: String): String {
        if (texto.isEmpty()) return texto

        return texto
            .lowercase(Locale.getDefault())
            // Reemplazar caracteres acentuados
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ñ", "n")
            // También manejar mayúsculas acentuadas
            .replace("Á", "a")
            .replace("É", "e")
            .replace("Í", "i")
            .replace("Ó", "o")
            .replace("Ú", "u")
            .replace("Ü", "u")
            .replace("Ñ", "n")
            // Quitar otros caracteres especiales comunes
            .replace(Regex("[^a-z0-9\\s]"), " ")
            // Normalizar espacios múltiples
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Implementa búsqueda flexible y robusta
     * - Búsqueda exacta
     * - Búsqueda por palabras parciales
     * - Búsqueda por prefijos/sufijos
     * - Manejo especial para letras individuales
     */
    private fun coincideBusquedaFlexible(query: String, carpeta: String, archivo: String): Boolean {
        if (query.isEmpty()) return true

        // Dividir la query en palabras para búsqueda más granular
        val palabrasQuery = query.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        // Función auxiliar para verificar si una palabra coincide
        fun palabraCoincide(palabra: String, texto: String): Boolean {
            if (palabra.isEmpty() || texto.isEmpty()) return false

            val palabrasTexto = texto.split("\\s+".toRegex())

            // Para letras individuales (longitud 1), ser más restrictivo
            if (palabra.length == 1) {
                // Solo buscar al inicio de palabras para letras individuales
                return palabrasTexto.any { it.startsWith(palabra) }
            }

            // Para palabras cortas (2-3 letras), ser moderadamente restrictivo
            if (palabra.length <= 3) {
                // Buscar al inicio de palabras o coincidencia exacta
                return palabrasTexto.any { it.startsWith(palabra) } || texto.contains(palabra)
            }

            // Para palabras más largas, búsqueda más flexible
            // 1. Coincidencia exacta
            if (texto.contains(palabra)) return true

            // 2. Coincidencia por prefijo (inicio de palabra)
            if (palabrasTexto.any { it.startsWith(palabra) }) return true

            // 3. Coincidencia por sufijo (fin de palabra)
            if (palabrasTexto.any { it.endsWith(palabra) }) return true

            // 4. Coincidencia parcial (contenida en cualquier parte de las palabras)
            if (palabrasTexto.any { it.contains(palabra) }) return true

            return false
        }

        // Verificar coincidencia en carpeta
        val carpetaCoincide = palabrasQuery.all { palabra ->
            palabraCoincide(palabra, carpeta)
        }

        // Verificar coincidencia en archivo
        val archivoCoincide = palabrasQuery.all { palabra ->
            palabraCoincide(palabra, archivo)
        }

        return carpetaCoincide || archivoCoincide
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
