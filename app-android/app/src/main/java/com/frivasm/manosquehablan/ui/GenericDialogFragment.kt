package com.frivasm.manosquehablan.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.frivasm.manosquehablan.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

/**
 * DialogFragment genérico que funciona correctamente con tamaños de fuente grandes.
 *
 * Características:
 * - Contenido scrollable con NestedScrollView
 * - Barra de acciones fija en la parte inferior
 * - Soporte completo para fontScale alto (1.0-2.0)
 * - Accesibilidad: foco lógico, targets táctiles ≥48dp, TalkBack
 */
class GenericDialogFragment : DialogFragment() {

    private var title: String? = null
    private var contentView: View? = null
    private var primaryButtonText: String? = null
    private var secondaryButtonText: String? = null
    private var onPrimaryClick: (() -> Unit)? = null
    private var onSecondaryClick: (() -> Unit)? = null

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_PRIMARY_BUTTON = "primary_button"
        private const val ARG_SECONDARY_BUTTON = "secondary_button"

        fun newInstance(
            title: String? = null,
            primaryButtonText: String? = null,
            secondaryButtonText: String? = null
        ): GenericDialogFragment {
            return GenericDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_PRIMARY_BUTTON, primaryButtonText)
                    putString(ARG_SECONDARY_BUTTON, secondaryButtonText)
                }
            }
        }
    }

    fun setContentView(view: View): GenericDialogFragment {
        this.contentView = view
        return this
    }

    fun setOnPrimaryClick(action: () -> Unit): GenericDialogFragment {
        this.onPrimaryClick = action
        return this
    }

    fun setOnSecondaryClick(action: () -> Unit): GenericDialogFragment {
        this.onSecondaryClick = action
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let { args ->
            title = args.getString(ARG_TITLE)
            primaryButtonText = args.getString(ARG_PRIMARY_BUTTON)
            secondaryButtonText = args.getString(ARG_SECONDARY_BUTTON)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Usar BottomSheetDialog para mejor UX en móviles
        val dialog = BottomSheetDialog(requireContext(), theme)

        // Configurar para expansión completa (sin colapso)
        dialog.behavior.apply {
            skipCollapsed = true
            isFitToContents = false
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            expandedOffset = 0
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_generic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configurar título
        val titleView = view.findViewById<MaterialTextView>(R.id.txtTitulo)
        title?.let {
            titleView.text = it
            titleView.visibility = View.VISIBLE
        } ?: run {
            titleView.visibility = View.GONE
        }

        // Configurar contenido
        val contentContainer = view.findViewById<ViewGroup>(R.id.contentScrollView)
        contentView?.let { customContent ->
            // Remover contenido por defecto y agregar el personalizado
            val scrollView = contentContainer as androidx.core.widget.NestedScrollView
            val linearLayout = scrollView.getChildAt(0) as ViewGroup

            // Limpiar contenido por defecto
            linearLayout.removeAllViews()

            // Agregar contenido personalizado
            linearLayout.addView(customContent)
        }

        // Configurar botones
        val btnPrimary = view.findViewById<MaterialButton>(R.id.btnPrimary)
        val btnSecondary = view.findViewById<MaterialButton>(R.id.btnSecondary)

        primaryButtonText?.let {
            btnPrimary.text = it
            btnPrimary.visibility = View.VISIBLE
            btnPrimary.setOnClickListener {
                onPrimaryClick?.invoke()
                dismiss()
            }
        } ?: run {
            btnPrimary.visibility = View.GONE
        }

        secondaryButtonText?.let {
            btnSecondary.text = it
            btnSecondary.visibility = View.VISIBLE
            btnSecondary.setOnClickListener {
                onSecondaryClick?.invoke()
                dismiss()
            }
        } ?: run {
            btnSecondary.visibility = View.GONE
        }

        // Configurar accesibilidad
        setupAccessibility(view)
    }

    private fun setupAccessibility(view: View) {
        // Asegurar orden de foco lógico usando nextFocusForward
        val contentScrollView = view.findViewById<View>(R.id.contentScrollView)
        val btnSecondary = view.findViewById<MaterialButton>(R.id.btnSecondary)
        val btnPrimary = view.findViewById<MaterialButton>(R.id.btnPrimary)

        // Establecer nextFocusForward para navegación por teclado
        contentScrollView.nextFocusForwardId = if (btnSecondary.visibility == View.VISIBLE) {
            btnSecondary.id
        } else {
            btnPrimary.id
        }

        btnSecondary.nextFocusForwardId = btnPrimary.id
        btnPrimary.nextFocusForwardId = contentScrollView.id

        // Asegurar targets táctiles ≥48dp (ya configurado en XML con minHeight)

        // Content descriptions para TalkBack
        view.contentDescription = title ?: getString(R.string.app_name)
    }

    override fun onStart() {
        super.onStart()

        // Asegurar que el BottomSheet esté completamente expandido
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            isFitToContents = false
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            expandedOffset = 0
        }
    }
}