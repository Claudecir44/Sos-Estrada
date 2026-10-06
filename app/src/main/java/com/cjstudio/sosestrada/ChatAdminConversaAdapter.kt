package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemChatAdminConversaBinding
import java.text.SimpleDateFormat
import java.util.Locale

// Lista do chip "💬 Chat Admin". Toque abre a conversa; segurar exclui.
// fotos: uid -> foto (base64) dos admins, carregado à parte (a conversa não
// guarda a foto, que pode mudar no Meu Perfil).
class ChatAdminConversaAdapter(
    private val aoTocar: (ConversaAdmin) -> Unit,
    private val aoSegurar: (ConversaAdmin) -> Unit
) : RecyclerView.Adapter<ChatAdminConversaAdapter.ViewHolder>() {

    private var conversas: List<ConversaAdmin> = emptyList()
    private var meuId: String? = null
    var fotos: Map<String, String?> = emptyMap()
        set(valor) { field = valor; notifyDataSetChanged() }

    private val hoje = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val formatoHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private val formatoData = SimpleDateFormat("dd/MM", Locale("pt", "BR"))

    class ViewHolder(val binding: ItemChatAdminConversaBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(lista: List<ConversaAdmin>, meuUid: String?) {
        conversas = lista
        meuId = meuUid
        notifyDataSetChanged()
    }

    fun fotoDe(conversa: ConversaAdmin) = fotos[conversa.idOutroAdmin(meuId)]

    override fun getItemCount() = conversas.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemChatAdminConversaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = conversas[position]
        with(holder.binding) {
            tvNomeChatAdmin.text = c.nomeOutroAdmin(meuId) ?: "Administrador"
            val previa = c.ultimaMensagem?.takeIf { it.isNotBlank() } ?: "Nenhuma mensagem ainda"
            tvPreviaChatAdmin.text = if (c.ultimoRemetenteId == meuId && !c.ultimaMensagem.isNullOrBlank()) "Você: $previa" else previa
            tvHoraChatAdmin.text = c.ultimoTimestamp?.let {
                if (hoje.format(it) == hoje.format(java.util.Date())) formatoHora.format(it) else formatoData.format(it)
            }.orEmpty()
            val naoLidas = c.naoLidasParaMim(meuId)
            tvNaoLidasChatAdmin.text = naoLidas.toString()
            tvNaoLidasChatAdmin.visibility = if (naoLidas > 0) View.VISIBLE else View.GONE
            FotoUtil.mostrar(ivFotoChatAdmin, fotoDe(c))
            root.setOnClickListener { aoTocar(c) }
            root.setOnLongClickListener { aoSegurar(c); true }
        }
    }
}
