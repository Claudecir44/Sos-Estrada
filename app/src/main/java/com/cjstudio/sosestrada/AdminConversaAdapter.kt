package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemAdminConversaBinding

class AdminConversaAdapter : RecyclerView.Adapter<AdminConversaAdapter.ViewHolder>() {

    private var solicitacoes: List<Solicitacao> = emptyList()

    class ViewHolder(val binding: ItemAdminConversaBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(novaLista: List<Solicitacao>) {
        solicitacoes = novaLista
        notifyDataSetChanged()
    }

    override fun getItemCount() = solicitacoes.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAdminConversaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = solicitacoes[position]
        with(holder.binding) {
            tvConversaMotorista.text = "🚗 ${s.motoristaNome ?: "Motorista"}"
            tvConversaPrestador.text = "🔧 ${s.prestadorNome ?: "Prestador"}"
            tvConversaStatus.mostrarStatus(s.status)
            tvConversaDataHora.text = dataHora(s.timestamp)
            root.setOnClickListener { abrirConversaComoAdmin(root.context, s) }
        }
    }
}
