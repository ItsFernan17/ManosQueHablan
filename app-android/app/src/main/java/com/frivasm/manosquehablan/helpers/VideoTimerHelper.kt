package com.frivasm.manosquehablan.helpers

import android.widget.TextView
import java.util.*

class VideoTimerHelper(private val temporizadorView: TextView) {
    
    private var timer: Timer? = null
    private var tiempoInicioGrabacion = 0L
    private var tiempoPausado = 0L
    private var tiempoInicioPausa = 0L
    private var segundosAcumulados = 0
    
    fun iniciarTemporizador() {
        // Si ya hay un timer corriendo, no crear uno nuevo
        if (timer != null) return
        
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // Calcular tiempo real considerando pausas
                val tiempoTranscurrido = System.currentTimeMillis() - tiempoInicioGrabacion - tiempoPausado
                val segundosReales = (tiempoTranscurrido / 1000).toInt()
                
                // Solo actualizar si hay un cambio real en los segundos
                if (segundosReales != segundosAcumulados) {
                    segundosAcumulados = segundosReales
                    val minutos = segundosReales / 60
                    val segundos = segundosReales % 60
                    val tiempoFormateado = String.format("%02d:%02d", minutos, segundos)
                    
                    // Actualizar UI en el hilo principal
                    temporizadorView.post {
                        temporizadorView.text = tiempoFormateado
                    }
                }
            }
        }, 100, 100) // Actualizar cada 100ms para mayor precisión
    }
    
    fun detenerTemporizador() {
        timer?.cancel()
        timer = null
    }
    
    fun pausarTemporizador() {
        detenerTemporizador()
        tiempoInicioPausa = System.currentTimeMillis()
    }
    
    fun reanudarTemporizador() {
        // Calcular cuánto tiempo estuvo pausado
        val tiempoPausadoActual = System.currentTimeMillis() - tiempoInicioPausa
        tiempoPausado += tiempoPausadoActual
        // Reiniciar el timer desde donde se quedó
        iniciarTemporizador()
    }
    
    fun setTiempoInicio(tiempo: Long) {
        tiempoInicioGrabacion = tiempo
        tiempoPausado = 0L
        segundosAcumulados = 0
    }
    
    fun setTiempoPausado(tiempo: Long) {
        tiempoPausado = tiempo
    }
    
    fun resetTemporizador() {
        detenerTemporizador()
        tiempoInicioGrabacion = 0L
        tiempoPausado = 0L
        tiempoInicioPausa = 0L
        segundosAcumulados = 0
        temporizadorView.text = "00:00"
    }
    
    fun getTiempoTranscurrido(): Long {
        return System.currentTimeMillis() - tiempoInicioGrabacion - tiempoPausado
    }
    
    fun cleanup() {
        detenerTemporizador()
    }
}
