package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cjstudio.sosestrada.databinding.ItemMensagemEnviadaBinding
import com.cjstudio.sosestrada.databinding.ItemMensagemRecebidaBinding
import java.text.SimpleDateFormat
import java.util.Locale

// Bolhas do chat. meuTipo = "motorista"/"prestador" do lado de quem abriu;
// null na visão somente leitura do admin (prestador fica à direita só pra
// diferenciar os lados, e cada bolha recebida mostra quem mandou).
class ChatAdapter(private val meuTipo: String?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mensagens: List<Mensagem> = emptyList()
    private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

    class EnviadaViewHolder(val binding: ItemMensagemEnviadaBinding) : RecyclerView.ViewHolder(binding.root)

    class RecebidaViewHolder(val binding: ItemMensagemRecebidaBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(novaLista: List<Mensagem>) {
        mensagens = novaLista
        notifyDataSetChanged()
    }

    override fun getItemCount() = mensagens.size

    override fun getItemViewType(position: Int): Int {
        val referencia = meuTipo ?: IChatRepository.PRESTADOR
        return if (mensagens[position].remetenteTipo == referencia) TIPO_ENVIADA else TIPO_RECEBIDA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_ENVIADA) {
            EnviadaViewHolder(ItemMensagemEnviadaBinding.inflate(inflater, parent, false))
        } else {
            RecebidaViewHolder(ItemMensagemRecebidaBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = mensagens[position]
        val hora = m.timestamp?.let { formatoHora.format(it) }.orEmpty()
        when (holder) {
            is EnviadaViewHolder -> with(holder.binding) {
                mostrarTextoEImagem(tvTextoMensagem, ivImagemMensagem, m)
                tvHoraMensagem.text = hora
                tvLidaMensagem.text = if (m.lida) "✓✓" else "✓"
                tvLidaMensagem.setTextColor(if (m.lida) 0xFF4FC3F7.toInt() else 0xFF757575.toInt())
            }
            is RecebidaViewHolder -> with(holder.binding) {
                mostrarTextoEImagem(tvTextoMensagem, ivImagemMensagem, m)
                tvHoraMensagem.text = hora
                if (meuTipo == null) {
                    tvRemetenteMensagem.visibility = View.VISIBLE
                    tvRemetenteMensagem.text = if (m.remetenteTipo == IChatRepository.MOTORISTA) "Motorista" else "Prestador"
                } else {
                    tvRemetenteMensagem.visibility = View.GONE
                }
            }
        }
    }

    private fun mostrarTextoEImagem(tvTexto: TextView, ivImagem: ImageView, m: Mensagem) {
        if (!m.imagemUrl.isNullOrEmpty()) {
            ivImagem.visibility = View.VISIBLE
            Glide.with(ivImagem).load(m.imagemUrl).into(ivImagem)
        } else {
            ivImagem.visibility = View.GONE
        }
        if (!m.texto.isNullOrEmpty()) {
            tvTexto.visibility = View.VISIBLE
            tvTexto.text = m.texto
        } else {
            tvTexto.visibility = View.GONE
        }
    }

    companion object {
        private const val TIPO_ENVIADA = 1
        private const val TIPO_RECEBIDA = 2
    }
}
