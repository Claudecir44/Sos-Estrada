package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

// Diálogo de avaliação (estrelas + comentário opcional) e o texto da nota
// mostrado nos cartões — usados pelo motorista (avalia o prestador) e pelo
// prestador (avalia o motorista).
object AvaliacaoDialogUtil {

    fun mostrar(context: Context, nomeAvaliado: String?, aoConfirmar: (nota: Int, comentario: String?) -> Unit) {
        val densidade = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * densidade).toInt(), (8 * densidade).toInt(), (20 * densidade).toInt(), 0)
        }
        val estrelas = RatingBar(context).apply {
            numStars = 5
            stepSize = 1f
            rating = 0f
        }
        layout.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(estrelas, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        layout.addView(TextView(context).apply {
            text = "Comentário (opcional)"
            setPadding(0, (10 * densidade).toInt(), 0, 0)
        })
        val comentario = EditText(context).apply {
            hint = "Como foi o atendimento?"
            minLines = 2
            filters = arrayOf(InputFilter.LengthFilter(AvaliacaoRepository.LIMITE_COMENTARIO))
        }
        layout.addView(comentario)

        AlertDialog.Builder(context)
            .setTitle("⭐ Avaliar ${nomeAvaliado?.trim()?.substringBefore(" ").orEmpty()}".trim())
            .setMessage("A avaliação aparece para os outros usuários e não pode ser alterada depois.")
            .setView(layout)
            .setPositiveButton("Enviar") { _, _ ->
                val nota = estrelas.rating.toInt()
                if (nota !in 1..5) {
                    Toast.makeText(context, "Escolha de 1 a 5 estrelas.", Toast.LENGTH_SHORT).show()
                } else {
                    aoConfirmar(nota, comentario.text.toString())
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun textoNota(nota: NotaUsuario?): String =
        if (nota == null || nota.total == 0) "⭐ Sem avaliações ainda"
        else String.format(Locale("pt", "BR"), "⭐ %.1f  (%d %s)", nota.media, nota.total, if (nota.total == 1) "avaliação" else "avaliações")
}
