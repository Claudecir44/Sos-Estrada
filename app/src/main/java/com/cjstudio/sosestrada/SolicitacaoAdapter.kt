package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.ACEITO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.CANCELADO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.PENDENTE
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.RECUSADO
import com.cjstudio.sosestrada.databinding.ItemSolicitacaoBinding

// Solicitações recebidas pelo prestador (AtendimentoActivity).
class SolicitacaoAdapter(
    private val aoAceitar: (Solicitacao) -> Unit,
    private val aoRecusar: (Solicitacao) -> Unit,
    private val aoExcluir: (Solicitacao) -> Unit
) : RecyclerView.Adapter<SolicitacaoAdapter.ViewHolder>() {

    private var solicitacoes: List<Solicitacao> = emptyList()

    class ViewHolder(val binding: ItemSolicitacaoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSolicitacaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = solicitacoes.size

    fun atualizarLista(novaLista: List<Solicitacao>) {
        solicitacoes = novaLista
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = solicitacoes[position]
        val b = holder.binding
        val context = b.root.context

        b.tvMotoristaNome.text = "Motorista: ${s.motoristaNome ?: ""}"
        b.tvMotoristaVeiculo.text = "🚗 Veículo: ${s.motoristaVeiculo ?: ""}"
        b.tvMotoristaPlaca.text = "🔢 Placa: ${s.motoristaPlaca ?: ""}"
        b.tvMotoristaTelefone.text = "📞 Telefone: ${s.motoristaTelefone ?: ""}"
        b.tvMotoristaEndereco.text = "📍 Endereço: ${s.enderecoMotorista ?: "não informado"}"

        val cancelada = s.status == CANCELADO
        b.tvCancelamento.visibility = if (cancelada) View.VISIBLE else View.GONE
        b.btnMensagem.visibility = if (cancelada) View.GONE else View.VISIBLE
        // Aceitar/recusar só enquanto está pendente.
        b.layoutBotoes.visibility = if (s.status == PENDENTE) View.VISIBLE else View.GONE

        if (s.naoLidasPrestador > 0) {
            val plural = if (s.naoLidasPrestador > 1) "s" else ""
            b.btnMensagem.text = "💬 Mensagem (${s.naoLidasPrestador} nova$plural)"
            b.btnMensagem.backgroundTintList = ColorStateList.valueOf(0xFFD32F2F.toInt())
        } else {
            b.btnMensagem.text = "💬 Mensagem"
            b.btnMensagem.backgroundTintList = ColorStateList.valueOf(0xFF9C27B0.toInt())
        }

        b.btnAceitar.setOnClickListener {
            if (s.status == PENDENTE) confirmarAceite(b, s)
            else Toast.makeText(context, "Esta solicitação já foi respondida.", Toast.LENGTH_SHORT).show()
        }
        b.btnRecusar.setOnClickListener {
            if (s.status == PENDENTE) aoRecusar(s)
            else Toast.makeText(context, "Esta solicitação já foi respondida.", Toast.LENGTH_SHORT).show()
        }
        b.btnMensagem.setOnClickListener {
            context.startActivity(
                Intent(context, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, s.id)
                    .putExtra(ChatActivity.EXTRA_MEU_TIPO, IChatRepository.PRESTADOR)
                    .putExtra(ChatActivity.EXTRA_TITULO, s.motoristaNome)
            )
        }
        // Segurar: exclusão permanente, só de solicitação cancelada.
        b.root.setOnLongClickListener {
            if (!cancelada) return@setOnLongClickListener false
            AlertDialog.Builder(context)
                .setTitle("Excluir permanentemente")
                .setMessage("Deseja excluir esta solicitação cancelada?")
                .setPositiveButton("Sim") { _, _ -> aoExcluir(s) }
                .setNegativeButton("Não", null)
                .show()
            true
        }
    }

    private fun confirmarAceite(b: ItemSolicitacaoBinding, s: Solicitacao) {
        val context = b.root.context
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_aceitar_solicitacao, null)
        val checkBox = view.findViewById<CheckBox>(R.id.checkBoxConfirmacao)
        view.findViewById<TextView>(R.id.tvMensagemConfirmacao).text =
            "Ao aceitar você concorda que estará indo socorrer o motorista, e será enviada uma mensagem ao mesmo, confirmando seu deslocamento."
        AlertDialog.Builder(context)
            .setTitle("Confirmar atendimento")
            .setView(view)
            .setPositiveButton("Aceitar") { _, _ ->
                if (checkBox.isChecked) aoAceitar(s)
                else Toast.makeText(context, "Marque a checkbox para confirmar.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        // Texto mostrado no Toast depois de atualizar o status.
        fun descricaoStatus(status: String) = when (status) {
            ACEITO -> "aceita"
            RECUSADO -> "recusada"
            else -> status
        }
    }
}
