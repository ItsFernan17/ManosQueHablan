package com.frivasm.manosquehablan.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.helpers.PositionValidator

/**
 * Banner superpuesto para mostrar avisos de posición dentro del recuadro de manos
 * Posición absoluta centrado en la parte inferior del contenedor guía
 */
class PositionValidationBanner(
    private val context: Context,
    private val handGuideContainer: View
) {
    
    companion object {
        private const val UI_THROTTLE_MS = 120L
        private const val ANIMATION_DURATION = 300L
    }

    private var bannerView: TextView? = null
    private var currentState: PositionValidator.PositionState? = null
    private var lastUIUpdate = 0L
    private val handler = Handler(Looper.getMainLooper())

    fun updatePosition(state: PositionValidator.PositionState, deviation: Float, uxAngle: Float, hasGyroscope: Boolean) {
        val currentTime = System.currentTimeMillis()
        
        // Throttle de UI ≥120 ms
        if (currentTime - lastUIUpdate < UI_THROTTLE_MS) return
        lastUIUpdate = currentTime

        handler.post {
            if (hasGyroscope) {
                // Si hay giroscopio, ocultar banner (el mensaje se muestra arriba)
                hideBanner()
            } else {
                // Solo mostrar banner para modo sin giroscopio
                when (state) {
                    PositionValidator.PositionState.GREEN -> {
                        hideBanner()
                    }
                    PositionValidator.PositionState.RED, PositionValidator.PositionState.CRITICAL -> {
                        showBanner(
                            "Sin giroscopio: coloca el teléfono verticalmente",
                            ContextCompat.getColor(context, R.color.error_color),
                            ContextCompat.getColor(context, android.R.color.white)
                        )
                    }
                }
            }
        }
    }

    private fun showBanner(message: String, backgroundColor: Int, textColor: Int) {
        // Si el banner ya existe con el mismo estado, solo actualizar texto
        if (bannerView != null && currentState != null) {
            bannerView?.text = message
            return
        }

        // Remover banner anterior si existe
        removeBanner()

        // Crear nuevo banner
        bannerView = TextView(context).apply {
            text = message
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(
                dpToPx(12f).toInt(),
                dpToPx(8f).toInt(),
                dpToPx(12f).toInt(),
                dpToPx(8f).toInt()
            )
            
            // Crear fondo redondeado
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dpToPx(8f)
            }
            
            // Configurar sombra
            elevation = dpToPx(4f)
        }

        // Configurar parámetros de layout para posición absoluta
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            bottomMargin = dpToPx(16f).toInt() // Margen desde el fondo del contenedor
            leftMargin = dpToPx(16f).toInt()
            rightMargin = dpToPx(16f).toInt()
        }

        // Añadir al contenedor padre (que debe ser un FrameLayout)
        val parentFrameLayout = handGuideContainer.parent as? FrameLayout
        if (parentFrameLayout != null) {
            // Iniciar con alpha 0 para animación
            bannerView?.alpha = 0f
            parentFrameLayout.addView(bannerView, layoutParams)
            
            // Animar entrada
            ObjectAnimator.ofFloat(bannerView, View.ALPHA, 0f, 1f).apply {
                duration = ANIMATION_DURATION
                start()
            }
        }

        currentState = when {
            message.contains("crítica") -> PositionValidator.PositionState.CRITICAL
            message.contains("Endereza") || message.contains("Sin giroscopio") -> PositionValidator.PositionState.RED
            else -> PositionValidator.PositionState.GREEN
        }
    }

    private fun hideBanner() {
        if (bannerView == null) return

        // Animar salida
        ObjectAnimator.ofFloat(bannerView, View.ALPHA, 1f, 0f).apply {
            duration = ANIMATION_DURATION
            start()
        }

        // Remover después de la animación
        handler.postDelayed({
            removeBanner()
        }, ANIMATION_DURATION)

        currentState = PositionValidator.PositionState.GREEN
    }

    private fun removeBanner() {
        bannerView?.let { banner ->
            val parentFrameLayout = banner.parent as? FrameLayout
            parentFrameLayout?.removeView(banner)
        }
        bannerView = null
        currentState = null
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        removeBanner()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
