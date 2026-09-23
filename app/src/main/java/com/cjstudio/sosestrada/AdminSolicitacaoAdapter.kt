package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemAdminSolicitacaoBinding

class AdminSolicitacaoAdapter : RecyclerView.Adapter<AdminSolicitacaoAdapter.ViewHolder>() {

    private var solicitacoes: List<Solicitacao> = emptyList()

    class ViewHolder(val binding: ItemAdminSolicitacaoBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(novaLista: List<Solicitacao>) {
        solicitacoes = novaLista
        notifyDataSetChanged()
    }

    override fun getItemCount() = solicitacoes.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAdminSolicitacaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = solicitacoes[position]
        with(holder.binding) {
            tvStatusAdmin.mostrarStatus(s.status)
            tvDataHoraAdmin.text = dataHora(s.timestamp)
            tvMotoristaNomeAdmin.text = s.motoristaNome ?: "Motorista"
            tvMotoristaVeiculoAdmin.text = listOfNotNull(s.motoristaVeiculo, s.motoristaPlaca)
                .filter { it.isNotBlank() }.joinToString(" • ").ifEmpty { "Veículo não informado" }
            tvMotoristaTelefoneAdmin.text = "📞 ${s.motoristaTelefone ?: "Não informado"}"
            tvMotoristaEnderecoAdmin.text = "📍 ${s.enderecoMotorista ?: "Endereço não informado"}"
            tvPrestadorNomeAdmin.text = s.prestadorNome ?: "Prestador"
            btnVerMensagensAdmin.setOnClickListener { abrirConversaComoAdmin(root.context, s) }
        }
    }
}
