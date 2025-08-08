package com.frivasm.manosquehablan.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.frivasm.manosquehablan.R
import com.google.android.material.bottomsheet.BottomSheetDialog

object OrdenamientoBottomSheetHelper {

    fun mostrarBottomSheetOrdenamiento(
        context: Context,
        onTipoSeleccionado: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_ordenamiento, null)
        dialog.setContentView(view)

        val opcionRecientes = view.findViewById<LinearLayout>(R.id.opcion_recientes)
        val opcionAlfabetico = view.findViewById<LinearLayout>(R.id.opcion_alfabetico)
        val opcionFecha = view.findViewById<LinearLayout>(R.id.opcion_fecha)

        val textoRecientes = view.findViewById<TextView>(R.id.texto_recientes)
        val textoAlfabetico = view.findViewById<TextView>(R.id.texto_alfabetico)
        val textoFecha = view.findViewById<TextView>(R.id.texto_fecha)

        val iconoRecientes = opcionRecientes.getChildAt(0) as ImageView
        val iconoAlfabetico = opcionAlfabetico.getChildAt(0) as ImageView
        val iconoFecha = opcionFecha.getChildAt(0) as ImageView

        val checkRecientes = view.findViewById<ImageView>(R.id.check_recientes)
        val checkAlfabetico = view.findViewById<ImageView>(R.id.check_alfabetico)
        val checkFecha = view.findViewById<ImageView>(R.id.check_fecha)

        val colorRojo = ContextCompat.getColor(context, R.color.rojo)
        val colorNormal = ContextCompat.getColor(context, R.color.texto_videos)

        fun aplicarSeleccion(tipo: String) {
            textoRecientes.setTextColor(colorNormal)
            textoAlfabetico.setTextColor(colorNormal)
            textoFecha.setTextColor(colorNormal)

            iconoRecientes.setColorFilter(colorNormal)
            iconoAlfabetico.setColorFilter(colorNormal)
            iconoFecha.setColorFilter(colorNormal)

            checkRecientes.visibility = View.GONE
            checkAlfabetico.visibility = View.GONE
            checkFecha.visibility = View.GONE

            when (tipo) {
                "recientes" -> {
                    textoRecientes.setTextColor(colorRojo)
                    iconoRecientes.setColorFilter(colorRojo)
                    checkRecientes.visibility = View.VISIBLE
                }
                "alfabetico" -> {
                    textoAlfabetico.setTextColor(colorRojo)
                    iconoAlfabetico.setColorFilter(colorRojo)
                    checkAlfabetico.visibility = View.VISIBLE
                }
                "fecha" -> {
                    textoFecha.setTextColor(colorRojo)
                    iconoFecha.setColorFilter(colorRojo)
                    checkFecha.visibility = View.VISIBLE
                }
            }

            PreferenciasHelper.guardarOrden(context, tipo)
            onTipoSeleccionado(tipo)
            dialog.dismiss()
        }

        val ordenActual = PreferenciasHelper.obtenerOrden(context)
        aplicarSeleccion(ordenActual)

        opcionRecientes.setOnClickListener { aplicarSeleccion("recientes") }
        opcionAlfabetico.setOnClickListener { aplicarSeleccion("alfabetico") }
        opcionFecha.setOnClickListener { aplicarSeleccion("fecha") }

        dialog.show()
    }
}
