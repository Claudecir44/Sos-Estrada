package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Bolhas do Chat Admin: os mesmos layouts do chat dos usuários
// (item_mensagem_enviada/recebida). Segurar a bolha abre "apagar".
class MensagemChatAdminAdapter(
    private val meuId: String,
    private val aoSegurar: (MensagemChatAdmin) -> Unit
) : RecyclerView.Adapter<MensagemChatAdminAdapter.Holder>() {

    private var mensagens: List<MensagemChatAdmin> = emptyList()
    private val formatoHora = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    fun atualizar(lista: List<MensagemChatAdmin>) {
        mensagens = lista
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val texto: TextView = view.findViewById(R.id.tvTextoMensagem)
        val hora: TextView = view.findViewById(R.id.tvHoraMensagem)
        val lida: TextView? = view.findViewById(R.id.tvLidaMensagem)
        val remetente: TextView? = view.findViewById(R.id.tvRemetenteMensagem)
    }

    override fun getItemViewType(position: Int) = if (mensagens[position].remetenteId == meuId) ENVIADA else RECEBIDA

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (viewType == ENVIADA) R.layout.item_mensagem_enviada else R.layout.item_mensagem_recebida
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = mensagens[position]
        val souEu = m.remetenteId == meuId
        val apagada = m.deletadaParaMim(meuId)
        holder.texto.text = when {
            m.deletadoParaTodos -> "⚠️ ${ChatAdminRepository.MENSAGEM_APAGADA}"
            apagada -> if (souEu) "Você apagou esta mensagem" else ChatAdminRepository.MENSAGEM_APAGADA
            else -> m.conteudo.orEmpty()
        }
        holder.texto.alpha = if (apagada) 0.6f else 1f
        holder.hora.text = m.timestamp?.let { formatoHora.format(it) }.orEmpty()
        holder.remetente?.apply {
            text = m.remetenteNome.orEmpty()
            visibility = if (m.remetenteNome.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        // ✓ enviada, ✓✓ azul lida (só nas minhas).
        holder.lida?.apply {
            visibility = if (apagada) View.GONE else View.VISIBLE
            text = if (m.lida) "✓✓" else "✓"
            setTextColor(if (m.lida) 0xFF2563EB.toInt() else 0xFF757575.toInt())
        }
        holder.itemView.setOnLongClickListener {
            if (!apagada) aoSegurar(m)
            true
        }
    }

    override fun getItemCount() = mensagens.size

    companion object {
        private const val ENVIADA = 0
        private const val RECEBIDA = 1
    }
}
