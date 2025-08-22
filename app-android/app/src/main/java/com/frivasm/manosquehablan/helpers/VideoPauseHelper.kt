package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.frivasm.manosquehablan.R
import com.frivasm.manosquehablan.databinding.ActivityGrabarVideoBinding

class VideoPauseHelper(
    private val context: Context,
    private val binding: ActivityGrabarVideoBinding,
    private val timerHelper: VideoTimerHelper
) {
    
    private var isPaused = false
    private var tiempoPausado = 0L
    private var tiempoInicioPausa = 0L
    
    fun pausarGrabacion() {
        try {
            isPaused = true
            tiempoInicioPausa = System.currentTimeMillis()
            // Pausar el temporizador
            timerHelper.pausarTemporizador()
            actualizarBotonPausar()
            binding.textIndicaciones.text = "Grabación pausada - Toca el botón naranja para continuar"
            // Vibración de feedback
            vibrar()
        } catch (e: Exception) {
            Log.e("VideoPause", "Error al pausar grabación: ${e.message}")
        }
    }
    
    fun reanudarGrabacion() {
        try {
            isPaused = false
            tiempoPausado += System.currentTimeMillis() - tiempoInicioPausa
            // Reanudar el temporizador
            timerHelper.reanudarTemporizador()
            actualizarBotonPausar()
            binding.textIndicaciones.text = "Grabando... Mantén tus manos visibles"
            // Vibración de feedback
            vibrar()
        } catch (e: Exception) {
            Log.e("VideoPause", "Error al reanudar grabación: ${e.message}")
        }
    }
    
    fun resetPauseState() {
        isPaused = false
        tiempoPausado = 0L
        tiempoInicioPausa = 0L
        // Forzar la actualización visual para limpiar completamente el estado
        actualizarBotonPausar()
    }
    
    fun limpiarCompletamente() {
        isPaused = false
        tiempoPausado = 0L
        tiempoInicioPausa = 0L
        
        // Limpiar completamente la UI
        binding.btnPausar.clearColorFilter()
        binding.btnPausar.background = ContextCompat.getDrawable(context, R.drawable.boton_cuadrado_redondeado)
        binding.btnPausar.setImageResource(R.drawable.icono_pausar)
        
        // Ocultar indicador de pausa
        binding.indicadorPausa.visibility = android.view.View.GONE
        
        // Restaurar botón de grabar
        binding.btnGrabar.isEnabled = true
        binding.btnGrabar.alpha = 1.0f
        binding.btnGrabar.setImageResource(R.drawable.circulo)
        
        // Ocultar overlay de pausa
        binding.overlayPausa.visibility = android.view.View.GONE
        
        // Restaurar temporizador al estilo inicial (rojo)
        binding.temporizador.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        binding.temporizador.background = ContextCompat.getDrawable(context, R.drawable.contador_background)
    }
    
    fun isPaused(): Boolean = isPaused
    
    fun getTiempoPausado(): Long = tiempoPausado
    
    private fun actualizarBotonPausar() {
        if (isPaused) {
            // Cambiar a color naranja/rojo para indicar pausa
            binding.btnPausar.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_orange_light))
            binding.btnPausar.background = ContextCompat.getDrawable(context, R.drawable.boton_pausa_activo)
            // Cambiar icono a play para indicar que se puede reanudar
            binding.btnPausar.setImageResource(R.drawable.reproducir)
            // Mostrar indicador de pausa con animación
            binding.indicadorPausa.startAnimation(android.view.animation.AnimationUtils.loadAnimation(context, R.anim.fade_in))
            binding.indicadorPausa.visibility = android.view.View.VISIBLE
            // Deshabilitar botón de grabar para evitar confusión
            binding.btnGrabar.isEnabled = false
            binding.btnGrabar.alpha = 0.5f
            // Cambiar drawable del botón de grabar a estado deshabilitado
            binding.btnGrabar.setImageResource(R.drawable.boton_grabar_deshabilitado)
            // Mostrar overlay de pausa en el botón de grabar
            binding.overlayPausa.visibility = android.view.View.VISIBLE
            // Cambiar color del temporizador a naranja para indicar pausa
            binding.temporizador.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
            binding.temporizador.background = ContextCompat.getDrawable(context, R.drawable.contador_pausa_background)
        } else {
            // Restaurar color normal
            binding.btnPausar.clearColorFilter()
            binding.btnPausar.background = ContextCompat.getDrawable(context, R.drawable.boton_cuadrado_redondeado)
            // Restaurar icono de pausa
            binding.btnPausar.setImageResource(R.drawable.icono_pausar)
            // Ocultar indicador de pausa con animación
            binding.indicadorPausa.startAnimation(android.view.animation.AnimationUtils.loadAnimation(context, R.anim.fade_out))
            binding.indicadorPausa.visibility = android.view.View.GONE
            // Habilitar botón de grabar
            binding.btnGrabar.isEnabled = true
            binding.btnGrabar.alpha = 1.0f
            // Restaurar drawable normal del botón de grabar
            binding.btnGrabar.setImageResource(R.drawable.circulo)
            // Ocultar overlay de pausa en el botón de grabar
            binding.overlayPausa.visibility = android.view.View.GONE
            // Restaurar color normal del temporizador al estilo inicial (rojo)
            binding.temporizador.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            binding.temporizador.background = ContextCompat.getDrawable(context, R.drawable.contador_background)
        }
    }
    
    private fun vibrar() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService(android.os.Vibrator::class.java)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.os.Vibrator::class.java)
            }
            
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoPause", "Error al vibrar: ${e.message}")
        }
    }
}
