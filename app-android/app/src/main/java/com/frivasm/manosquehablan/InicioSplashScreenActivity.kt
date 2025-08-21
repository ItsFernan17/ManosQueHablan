package com.frivasm.manosquehablan

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity

class InicioSplashScreenActivity : AppCompatActivity() {
    
    private lateinit var loadingContainer: View
    private lateinit var loadingDot1_rojo: android.widget.ImageView
    private lateinit var loadingDot1_celeste: android.widget.ImageView
    private lateinit var loadingDot2_rojo: android.widget.ImageView
    private lateinit var loadingDot2_celeste: android.widget.ImageView
    private lateinit var loadingDot3_rojo: android.widget.ImageView
    private lateinit var loadingDot3_celeste: android.widget.ImageView
    
    private val celesteColor = Color.parseColor("#0376ED")
    private val rojoColor = Color.parseColor("#ed7971")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio_splash_screen)
        
        // Inicializar vistas
        initializeViews()
        
        // Iniciar secuencia de puntos de carga
        startLoadingSequence()
    }
    
    private fun initializeViews() {
        loadingContainer = findViewById(R.id.loadingContainer)
        loadingDot1_rojo = findViewById(R.id.loadingDot1_rojo)
        loadingDot1_celeste = findViewById(R.id.loadingDot1_celeste)
        loadingDot2_rojo = findViewById(R.id.loadingDot2_rojo)
        loadingDot2_celeste = findViewById(R.id.loadingDot2_celeste)
        loadingDot3_rojo = findViewById(R.id.loadingDot3_rojo)
        loadingDot3_celeste = findViewById(R.id.loadingDot3_celeste)
        
        // Inicializar puntos como invisibles
        loadingDot1_rojo.alpha = 0f
        loadingDot1_celeste.alpha = 0f
        loadingDot2_rojo.alpha = 0f
        loadingDot2_celeste.alpha = 0f
        loadingDot3_rojo.alpha = 0f
        loadingDot3_celeste.alpha = 0f
    }
    
    private fun startLoadingSequence() {
        // Primera secuencia de puntos con aparición
        startAnimatedLoading()
        
        // Segunda secuencia después de que termine completamente la primera
        Handler(Looper.getMainLooper()).postDelayed({
            // Esperar a que termine la primera secuencia completa
            Handler(Looper.getMainLooper()).postDelayed({
                // Segunda secuencia solo con saltos (sin aparición)
                startJumpOnlySequence()
                // Navegar a la app después de que termine la segunda secuencia
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, InicioAppActivity::class.java)
                    startActivity(intent)
                    finish()
                }, 1800) // 1.8 segundos para la segunda secuencia completa
            }, 500) // 0.5 segundos de pausa entre secuencias
        }, 1800) // 1.8 segundos para la primera secuencia completa
    }
    
    private fun startAnimatedLoading() {
        // Crear listas de puntos rojos y celestes
        val dotsRojo = listOf(loadingDot1_rojo, loadingDot2_rojo, loadingDot3_rojo)
        val dotsCeleste = listOf(loadingDot1_celeste, loadingDot2_celeste, loadingDot3_celeste)
        
        dotsRojo.forEachIndexed { index, dotRojo ->
            val dotCeleste = dotsCeleste[index]
            
            // Animación de aparición más rápida y elegante
            val fadeIn = ObjectAnimator.ofFloat(dotRojo, "alpha", 0f, 1f)
            val scaleUp = ObjectAnimator.ofFloat(dotRojo, "scaleX", 0.3f, 1.1f, 1f)
            val scaleY = ObjectAnimator.ofFloat(dotRojo, "scaleY", 0.3f, 1.1f, 1f)
            
            val appearAnim = AnimatorSet().apply {
                playTogether(fadeIn, scaleUp, scaleY)
                duration = 400
                startDelay = index * 150L
                interpolator = android.view.animation.OvershootInterpolator(0.8f)
            }
            
            appearAnim.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Iniciar animación de salto después de aparecer
                    startJumpAnimation(dotRojo, dotCeleste, index, true) // true = primer salto
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            appearAnim.start()
        }
    }
    
    private fun startJumpOnlySequence() {
        // Crear listas de puntos rojos y celestes
        val dotsRojo = listOf(loadingDot1_rojo, loadingDot2_rojo, loadingDot3_rojo)
        val dotsCeleste = listOf(loadingDot1_celeste, loadingDot2_celeste, loadingDot3_celeste)
        
        // Iniciar saltos directamente sin animación de aparición
        dotsRojo.forEachIndexed { index, dotRojo ->
            val dotCeleste = dotsCeleste[index]
            
            // Iniciar animación de salto directamente
            Handler(Looper.getMainLooper()).postDelayed({
                startJumpAnimation(dotRojo, dotCeleste, index, false) // false = segundo salto
            }, index * 300L) // 300ms entre cada punto (más rápido)
        }
    }
    
    private fun startJumpAnimation(dotRojo: android.widget.ImageView, dotCeleste: android.widget.ImageView, index: Int, isFirstJump: Boolean) {
        // Función para levantar el punto y cambiar de color
        val jumpAndChange = {
            if (isFirstJump) {
                // PRIMER SALTO: Animar el punto rojo
                val liftUp = ObjectAnimator.ofFloat(dotRojo, "translationY", 0f, -30f)
                val scaleUp = ObjectAnimator.ofFloat(dotRojo, "scaleX", 1f, 1.3f, 1.1f)
                val scaleY = ObjectAnimator.ofFloat(dotRojo, "scaleY", 1f, 1.3f, 1.1f)
                
                val jumpAnim = AnimatorSet().apply {
                    playTogether(liftUp, scaleUp, scaleY)
                    duration = 500
                    interpolator = android.view.animation.BounceInterpolator()
                }
                
                jumpAnim.addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Cambiar de rojo a celeste y quedarse en celeste
                        val fadeOut = ObjectAnimator.ofFloat(dotRojo, "alpha", 1f, 0f)
                        val fadeIn = ObjectAnimator.ofFloat(dotCeleste, "alpha", 0f, 1f)
                        
                        val colorChangeAnim = AnimatorSet().apply {
                            playTogether(fadeOut, fadeIn)
                            duration = 150
                            interpolator = AccelerateDecelerateInterpolator()
                        }
                        
                        colorChangeAnim.start()
                        
                        // Mantener el punto arriba por un momento más corto
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Bajar el punto con animación más rápida
                            val lowerDown = ObjectAnimator.ofFloat(dotRojo, "translationY", -30f, 0f)
                            val scaleDown = ObjectAnimator.ofFloat(dotRojo, "scaleX", 1.1f, 0.9f, 1f)
                            val scaleDownY = ObjectAnimator.ofFloat(dotRojo, "scaleY", 1.1f, 0.9f, 1f)
                            
                            val lowerAnim = AnimatorSet().apply {
                                playTogether(lowerDown, scaleDown, scaleDownY)
                                duration = 400
                                interpolator = android.view.animation.AnticipateOvershootInterpolator(1.2f)
                            }
                            
                            lowerAnim.start()
                        }, 300) // Mantener arriba por 300ms (más corto)
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {}
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                jumpAnim.start()
                
            } else {
                // SEGUNDO SALTO: Animar el punto celeste (que está visible)
                val liftUp = ObjectAnimator.ofFloat(dotCeleste, "translationY", 0f, -30f)
                val scaleUp = ObjectAnimator.ofFloat(dotCeleste, "scaleX", 1f, 1.3f, 1.1f)
                val scaleY = ObjectAnimator.ofFloat(dotCeleste, "scaleY", 1f, 1.3f, 1.1f)
                
                val jumpAnim = AnimatorSet().apply {
                    playTogether(liftUp, scaleUp, scaleY)
                    duration = 500
                    interpolator = android.view.animation.BounceInterpolator()
                }
                
                jumpAnim.addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Cambiar de celeste a rojo y quedarse en rojo
                        val fadeOut = ObjectAnimator.ofFloat(dotCeleste, "alpha", 1f, 0f)
                        val fadeIn = ObjectAnimator.ofFloat(dotRojo, "alpha", 0f, 1f)
                        
                        val colorChangeAnim = AnimatorSet().apply {
                            playTogether(fadeOut, fadeIn)
                            duration = 150
                            interpolator = AccelerateDecelerateInterpolator()
                        }
                        
                        colorChangeAnim.start()
                        
                        // Mantener el punto arriba por un momento más corto
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Bajar el punto con animación más rápida
                            val lowerDown = ObjectAnimator.ofFloat(dotCeleste, "translationY", -30f, 0f)
                            val scaleDown = ObjectAnimator.ofFloat(dotCeleste, "scaleX", 1.1f, 0.9f, 1f)
                            val scaleDownY = ObjectAnimator.ofFloat(dotCeleste, "scaleY", 1.1f, 0.9f, 1f)
                            
                            val lowerAnim = AnimatorSet().apply {
                                playTogether(lowerDown, scaleDown, scaleDownY)
                                duration = 400
                                interpolator = android.view.animation.AnticipateOvershootInterpolator(1.2f)
                            }
                            
                            lowerAnim.start()
                        }, 300) // Mantener arriba por 300ms (más corto)
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {}
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                jumpAnim.start()
            }
        }
        
        // Iniciar la secuencia con delay secuencial más rápido
        Handler(Looper.getMainLooper()).postDelayed({
            jumpAndChange()
        }, index * 300L) // 300ms entre cada punto (más rápido)
    }
    
    private fun hideLoadingDots() {
        // Ocultar todos los puntos
        loadingDot1_rojo.alpha = 0f
        loadingDot1_celeste.alpha = 0f
        loadingDot2_rojo.alpha = 0f
        loadingDot2_celeste.alpha = 0f
        loadingDot3_rojo.alpha = 0f
        loadingDot3_celeste.alpha = 0f
    }
}