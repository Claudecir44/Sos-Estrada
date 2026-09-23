package com.cjstudio.sosestrada

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Funções comuns às listas do painel admin (só leitura).

private val formatoDataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

internal fun dataHora(data: Date?) = "Data/Hora: " + (data?.let { formatoDataHora.format(it) } ?: "não informada")

// Chat da solicitação em modo somente leitura.
fun abrirConversaComoAdmin(context: Context, s: Solicitacao) {
    context.startActivity(
        Intent(context, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, s.id)
            .putExtra(ChatActivity.EXTRA_READ_ONLY, true)
            .putExtra(ChatActivity.EXTRA_TITULO, "${s.motoristaNome} ↔ ${s.prestadorNome}")
    )
}
