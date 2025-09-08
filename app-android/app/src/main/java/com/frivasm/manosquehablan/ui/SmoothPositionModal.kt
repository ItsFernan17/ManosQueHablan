package com.frivasm.manosquehablan.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.helpers.PositionValidator

/**
 * Modal suave y bonita para validación de posición con animación de teléfono
 */
class SmoothPositionModal(
    private val context: Context,
    private val parentView: View
) {
    
    companion object {
        private const val ANIMATION_DURATION = 400L // Más suave
        private const val UPDATE_SMOOTHNESS_THRESHOLD = 1.5f // Más sensible
        private const val BACKGROUND_ALPHA = 0.75f // 75% transparencia
    }

    private var modalContainer: FrameLayout? = null
    private var backgroundOverlay: View? = null
    private var messageText: TextView? = null
    private var angleText: TextView? = null
    private var phoneIcon: ImageView? = null
    private var currentState: PositionValidator.PositionState? = null
    private var lastAngle = 0f
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Variables para detección de movimiento
    private var lastMovementTime = 0L
    private var isMoving = false
    private var previousAngle = 0f
    private var verificationRunnable: Runnable? = null

    fun updatePosition(state: PositionValidator.PositionState, deviation: Float, uxAngle: Float, hasGyroscope: Boolean) {
        handler.post {
            // Detectar movimiento del teléfono
            val currentTime = System.currentTimeMillis()
            val angleChange = kotlin.math.abs(uxAngle - previousAngle)
            
            if (angleChange > 2f) { // Threshold para detectar movimiento significativo
                lastMovementTime = currentTime
                if (!isMoving) {
                    isMoving = true
                    showVerifyingMessage()
                }
                // Cancelar verificación previa si existe
                verificationRunnable?.let { handler.removeCallbacks(it) }
                
                // Programar verificación después de 1 segundo de inactividad
                verificationRunnable = Runnable {
                    isMoving = false
                    checkFinalPosition(state, uxAngle, hasGyroscope)
                }
                handler.postDelayed(verificationRunnable!!, 1000)
            }
            
            previousAngle = uxAngle
            
            // Si no está en movimiento, usar lógica original
            if (!isMoving) {
                when (state) {
                    PositionValidator.PositionState.GREEN -> {
                        hideModalSmoothly()
                    }
                    PositionValidator.PositionState.RED, PositionValidator.PositionState.CRITICAL -> {
                        if (hasGyroscope) {
                            // Solo actualizar si hay cambio significativo para suavidad
                            if (!isShowing || kotlin.math.abs(uxAngle - lastAngle) > UPDATE_SMOOTHNESS_THRESHOLD || currentState != state) {
                                showModalSmoothly(state, uxAngle, hasGyroscope)
                                lastAngle = uxAngle
                            } else {
                                // Actualización suave solo del ángulo
                                updateAngleSmoothly(uxAngle)
                            }
                        } else {
                            showModalSmoothly(state, uxAngle, hasGyroscope)
                        }
                    }
                }
            }
        }
    }

    private fun showModalSmoothly(state: PositionValidator.PositionState, uxAngle: Float, hasGyroscope: Boolean) {
        if (modalContainer == null) {
            createModal()
        }

        currentState = state
        isShowing = true

        // Actualizar contenido
        val message = when (state) {
            PositionValidator.PositionState.RED -> {
                if (hasGyroscope) {
                    "Endereza tu teléfono para continuar"
                } else {
                    "Sin giroscopio: coloca el teléfono verticalmente"
                }
            }
            PositionValidator.PositionState.CRITICAL -> {
                if (hasGyroscope) {
                    "¡Posición crítica! Ajusta la orientación"
                } else {
                    "Sin giroscopio: coloca el teléfono verticalmente"
                }
            }
            else -> ""
        }

        messageText?.text = message
        
        if (hasGyroscope) {
            angleText?.text = "Ángulo actual: ${String.format("%.0f", uxAngle)}° (ideal: 70-90°)"
            angleText?.visibility = View.VISIBLE
            // Ya no necesitamos animación del ícono warning
        } else {
            angleText?.visibility = View.GONE
        }

        // Color según estado
        val backgroundColor = when (state) {
            PositionValidator.PositionState.CRITICAL -> ContextCompat.getColor(context, R.color.critical_color)
            else -> ContextCompat.getColor(context, R.color.error_color)
        }

        val background = modalContainer?.background as? GradientDrawable
        background?.setColor(backgroundColor)

        // Mostrar con animación suave si no está visible
        if (modalContainer?.alpha == 0f) {
            // Animar fondo
            backgroundOverlay?.let { bg ->
                ObjectAnimator.ofFloat(bg, View.ALPHA, 0f, BACKGROUND_ALPHA).apply {
                    duration = ANIMATION_DURATION
                    start()
                }
            }
            
            // Animar modal
            val fadeIn = ObjectAnimator.ofFloat(modalContainer, View.ALPHA, 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(modalContainer, View.SCALE_X, 0.7f, 1f)
            val scaleY = ObjectAnimator.ofFloat(modalContainer, View.SCALE_Y, 0.7f, 1f)

            AnimatorSet().apply {
                playTogether(fadeIn, scaleX, scaleY)
                duration = ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun updateAngleSmoothly(uxAngle: Float) {
        angleText?.text = "Ángulo actual: ${String.format("%.0f", uxAngle)}° (ideal: 70-90°)"
        lastAngle = uxAngle
    }

    private fun hideModalSmoothly() {
        if (modalContainer == null || !isShowing) return

        isShowing = false

        // Animar fondo
        backgroundOverlay?.let { bg ->
            ObjectAnimator.ofFloat(bg, View.ALPHA, BACKGROUND_ALPHA, 0f).apply {
                duration = ANIMATION_DURATION
                start()
            }
        }

        // Animar modal
        val fadeOut = ObjectAnimator.ofFloat(modalContainer, View.ALPHA, 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(modalContainer, View.SCALE_X, 1f, 0.7f)
        val scaleY = ObjectAnimator.ofFloat(modalContainer, View.SCALE_Y, 1f, 0.7f)

        AnimatorSet().apply {
            playTogether(fadeOut, scaleX, scaleY)
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeModal()
                }
            })
            start()
        }
    }

    private fun createModal() {
        val parentFrameLayout = findParentFrameLayout() ?: return

        // Crear fondo negro semi-transparente
        backgroundOverlay = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            alpha = 0f
        }
        
        val backgroundLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        parentFrameLayout.addView(backgroundOverlay, backgroundLayoutParams)

        // Crear modal más grande
        modalContainer = FrameLayout(context).apply {
            // Fondo con esquinas redondeadas
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.error_color))
                cornerRadius = dpToPx(20f) // Esquinas más redondeadas
            }
            elevation = dpToPx(12f) // Más elevación
            alpha = 0f
        }

        // Layout params para modal más grande
        val modalLayoutParams = FrameLayout.LayoutParams(
            (dpToPx(320f)).toInt(), // Más ancha
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Contenido de la modal con más padding
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(32f).toInt(), // Más padding
                dpToPx(28f).toInt(),
                dpToPx(32f).toInt(),
                dpToPx(28f).toInt()
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Ícono de warning más grande
        phoneIcon = ImageView(context).apply {
            setImageResource(R.drawable.warning)
            setColorFilter(ContextCompat.getColor(context, android.R.color.white))
        }
        val phoneLayoutParams = LinearLayout.LayoutParams(
            dpToPx(80f).toInt(), // Más grande
            dpToPx(80f).toInt()
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dpToPx(20f).toInt()
        }
        contentLayout.addView(phoneIcon, phoneLayoutParams)

        // Texto del mensaje principal más grande
        messageText = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) // Texto más grande
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val messageLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(12f).toInt()
        }
        contentLayout.addView(messageText, messageLayoutParams)

        // Texto del ángulo más grande
        angleText = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Texto más grande
            gravity = Gravity.CENTER
            alpha = 0.9f
        }
        val angleLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentLayout.addView(angleText, angleLayoutParams)

        modalContainer?.addView(contentLayout)
        parentFrameLayout.addView(modalContainer, modalLayoutParams)
    }
    
    private fun showVerifyingMessage() {
        if (modalContainer == null) {
            createModal()
        }
        
        isShowing = true
        
        // Mostrar mensaje de verificación
        messageText?.text = "Verificando ángulo..."
        angleText?.text = "Espera un momento..."
        
        // Color amarillo para indicar verificación
        val verifyingColor = ContextCompat.getColor(context, R.color.celeste)
        (modalContainer?.background as? GradientDrawable)?.setColor(verifyingColor)
        
        // Mostrar modal si no está visible
        modalContainer?.let { container ->
            if (container.alpha == 0f) {
                container.visibility = View.VISIBLE
                
                // Animar fondo
                backgroundOverlay?.let { bg ->
                    ObjectAnimator.ofFloat(bg, View.ALPHA, 0f, BACKGROUND_ALPHA).apply {
                        duration = ANIMATION_DURATION
                        start()
                    }
                }
                
                container.animate()
                    .alpha(1f)
                    .setDuration(ANIMATION_DURATION)
                    .start()
            }
        }
    }
    
    // Callback para notificar cuando el flujo de verificación está completo
    var onVerificationComplete: (() -> Unit)? = null
    
    // Referencia al validador de posición para poder notificar la finalización
    var positionValidator: PositionValidator? = null
    
    private fun checkFinalPosition(state: PositionValidator.PositionState, uxAngle: Float, hasGyroscope: Boolean) {
        // Verificar la posición final después del movimiento
        when (state) {
            PositionValidator.PositionState.GREEN -> {
                // Posición correcta - mostrar mensaje de éxito brevemente
                showSuccessMessage()
                
                // Primero mostrar el mensaje de éxito durante 800ms
                // y luego notificar que el flujo está completo y ocultar el modal
                handler.postDelayed({ 
                    // Notificar al validador de posición que la verificación está completa
                    positionValidator?.completeAngleVerification()
                    
                    // Notificar que el flujo está completo (callback adicional si es necesario)
                    onVerificationComplete?.invoke()
                    
                    // Ocultar el modal
                    hideModalSmoothly() 
                }, 800)
            }
            PositionValidator.PositionState.RED, PositionValidator.PositionState.CRITICAL -> {
                // Posición incorrecta - mostrar error
                showModalSmoothly(state, uxAngle, hasGyroscope)
            }
        }
    }
    
    private fun showSuccessMessage() {
        messageText?.text = "¡Ángulo correcto!"
        angleText?.text = "Posición verificada"
        
        // Color verde para éxito
        val successColor = ContextCompat.getColor(context, R.color.violeta)
        (modalContainer?.background as? GradientDrawable)?.setColor(successColor)
    }

    private fun findParentFrameLayout(): FrameLayout? {
        var parent = parentView.parent
        while (parent != null) {
            if (parent is FrameLayout) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun removeModal() {
        modalContainer?.let { container ->
            val parentFrameLayout = container.parent as? FrameLayout
            parentFrameLayout?.removeView(container)
        }
        backgroundOverlay?.let { overlay ->
            val parentFrameLayout = overlay.parent as? FrameLayout
            parentFrameLayout?.removeView(overlay)
        }
        modalContainer = null
        backgroundOverlay = null
        currentState = null
    }

    /**
     * Fuerza la aparición inmediata del banner para dar feedback instantáneo al usuario
     * cuando toca el botón de grabar
     */
    fun forceShowBannerOnButtonPress() {
        handler.post {
            if (modalContainer == null) {
                createModal()
            }
            
            // Mostrar mensaje de verificación inmediata
            messageText?.text = "Verificando posición del teléfono..."
            angleText?.text = "Espera un momento..."
            angleText?.visibility = View.VISIBLE
            
            // Color de verificación (azul)
            val verifyingColor = ContextCompat.getColor(context, R.color.celeste)
            (modalContainer?.background as? GradientDrawable)?.setColor(verifyingColor)
            
            isShowing = true
            
            // Mostrar modal inmediatamente
            modalContainer?.let { container ->
                container.visibility = View.VISIBLE
                container.alpha = 0f
                
                // Animar fondo
                backgroundOverlay?.let { bg ->
                    bg.visibility = View.VISIBLE
                    ObjectAnimator.ofFloat(bg, View.ALPHA, 0f, BACKGROUND_ALPHA).apply {
                        duration = ANIMATION_DURATION
                        start()
                    }
                }
                
                // Animar modal con entrada rápida y suave
                val fadeIn = ObjectAnimator.ofFloat(container, View.ALPHA, 0f, 1f)
                val scaleX = ObjectAnimator.ofFloat(container, View.SCALE_X, 0.8f, 1f)
                val scaleY = ObjectAnimator.ofFloat(container, View.SCALE_Y, 0.8f, 1f)

                AnimatorSet().apply {
                    playTogether(fadeIn, scaleX, scaleY)
                    duration = ANIMATION_DURATION
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        }
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        verificationRunnable?.let { handler.removeCallbacks(it) }
        removeModal()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
