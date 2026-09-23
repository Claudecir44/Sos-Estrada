package com.cjstudio.sosestrada

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Funções comuns às listas do painel admin (só leitura).

private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())

internal fun dataHora(data: Date?) = data?.let { formatoDataHora.format(it) } ?: "Sem data"

// Até duas iniciais do nome, pro avatar redondo ("Maria Souza" -> "MS").
internal fun iniciais(nome: String?): String =
    nome.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

// Etiqueta colorida do status da solicitação.
internal fun TextView.mostrarStatus(status: String?) {
    val (rotulo, fundo, cor) = when (status) {
        ISolicitacaoRepository.PENDENTE -> Triple("⏳ Pendente", R.color.admin_laranja_claro, R.color.admin_laranja)
        ISolicitacaoRepository.ACEITO -> Triple("✅ Aceito", R.color.admin_verde_claro, R.color.admin_verde)
        ISolicitacaoRepository.RECUSADO -> Triple("❌ Recusado", R.color.admin_vermelho_claro, R.color.admin_vermelho)
        ISolicitacaoRepository.CANCELADO -> Triple("🚫 Cancelado", R.color.admin_chip, R.color.admin_texto_secundario)
        else -> Triple(status?.replaceFirstChar { it.uppercase() } ?: "Sem status", R.color.admin_chip, R.color.admin_texto)
    }
    text = rotulo
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, fundo))
    setTextColor(ContextCompat.getColor(context, cor))
}

// Chat da solicitação em modo somente leitura.
fun abrirConversaComoAdmin(context: Context, s: Solicitacao) {
    context.startActivity(
        Intent(context, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, s.id)
            .putExtra(ChatActivity.EXTRA_READ_ONLY, true)
            .putExtra(ChatActivity.EXTRA_TITULO, "${s.motoristaNome} ↔ ${s.prestadorNome}")
    )
}
