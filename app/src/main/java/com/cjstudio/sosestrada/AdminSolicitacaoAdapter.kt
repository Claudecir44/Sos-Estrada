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
            tvMotoristaNomeAdmin.text = "Motorista: ${s.motoristaNome}"
            tvMotoristaTelefoneAdmin.text = "📞 ${s.motoristaTelefone}"
            tvMotoristaVeiculoAdmin.text = "🚗 ${s.motoristaVeiculo}"
            tvMotoristaPlacaAdmin.text = "🔢 ${s.motoristaPlaca}"
            tvMotoristaEnderecoAdmin.text = "📍 ${s.enderecoMotorista ?: "não informado"}"
            tvPrestadorNomeAdmin.text = "Prestador: ${s.prestadorNome}"
            tvStatusAdmin.text = "Status: ${s.status}"
            tvStatusAdmin.setTextColor(
                when (s.status) {
                    ISolicitacaoRepository.PENDENTE -> 0xFFFF9800
                    ISolicitacaoRepository.ACEITO -> 0xFF4CAF50
                    ISolicitacaoRepository.RECUSADO -> 0xFFD32F2F
                    ISolicitacaoRepository.CANCELADO -> 0xFF9E9E9E
                    else -> 0xFF333333
                }.toInt()
            )
            tvDataHoraAdmin.text = dataHora(s.timestamp)
            btnVerMensagensAdmin.setOnClickListener { abrirConversaComoAdmin(root.context, s) }
        }
    }
}
