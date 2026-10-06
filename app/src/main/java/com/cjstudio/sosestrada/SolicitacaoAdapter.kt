package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Locale

// Solicitações recebidas pelo prestador (AtendimentoFragment).
class SolicitacaoAdapter(
    private val aoAceitar: (Solicitacao) -> Unit,
    private val aoRecusar: (Solicitacao) -> Unit,
    private val aoExcluir: (Solicitacao) -> Unit,
    // Prestador abriu a localização que o motorista enviou: apaga o alerta.
    private val aoVerLocalizacao: (Solicitacao) -> Unit,
    private val aoAvaliar: (Solicitacao) -> Unit
) : RecyclerView.Adapter<SolicitacaoAdapter.ViewHolder>() {

    // Nota de cada motorista (uid -> média/total) e solicitações que o
    // prestador já avaliou.
    private var notas: Map<String, NotaUsuario> = emptyMap()
    private var jaAvaliadas: Set<String> = emptySet()

    fun atualizarAvaliacoes(notas: Map<String, NotaUsuario>, jaAvaliadas: Set<String>) {
        this.notas = notas
        this.jaAvaliadas = jaAvaliadas
        notifyDataSetChanged()
    }

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
        b.tvAvaliacaoMotorista.text = AvaliacaoDialogUtil.textoNota(s.motoristaUid?.let { notas[it] })
        val podeAvaliar = s.status == ACEITO && !s.id.isNullOrEmpty() && s.id !in jaAvaliadas
        b.btnAvaliarMotorista.visibility = if (podeAvaliar) View.VISIBLE else View.GONE
        b.btnAvaliarMotorista.setOnClickListener { aoAvaliar(s) }
        b.tvMotoristaVeiculo.text = "🚗 Veículo: ${s.motoristaVeiculo ?: ""}"
        b.tvMotoristaPlaca.text = "🔢 Placa: ${s.motoristaPlaca ?: ""}"
        b.tvMotoristaTelefone.text = "📞 Telefone: ${s.motoristaTelefone ?: ""}"
        b.tvMotoristaEndereco.text = "📍 Endereço: ${s.enderecoMotorista ?: "não informado"}"

        val cancelada = s.status == CANCELADO
        b.tvCancelamento.visibility = if (cancelada) View.VISIBLE else View.GONE
        b.containerMensagem.visibility = if (cancelada) View.GONE else View.VISIBLE
        // Aceitar/recusar só enquanto está pendente.
        b.layoutBotoes.visibility = if (s.status == PENDENTE) View.VISIBLE else View.GONE

        BadgeUtil.mostrar(b.badgeMensagem, s.naoLidasPrestador)

        b.btnAceitar.setOnClickListener {
            if (s.status == PENDENTE) confirmarAceite(b, s)
            else Toast.makeText(context, "Esta solicitação já foi respondida.", Toast.LENGTH_SHORT).show()
        }
        b.btnRecusar.setOnClickListener {
            if (s.status == PENDENTE) aoRecusar(s)
            else Toast.makeText(context, "Esta solicitação já foi respondida.", Toast.LENGTH_SHORT).show()
        }
        mostrarLocalizacaoMotorista(b, s, cancelada)
        b.btnMensagem.setOnClickListener {
            context.startActivity(
                Intent(context, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, s.id)
                    .putExtra(ChatActivity.EXTRA_MEU_TIPO, IChatRepository.PRESTADOR)
                    .putExtra(ChatActivity.EXTRA_TITULO, s.motoristaNome)
                    .putExtra(ChatActivity.EXTRA_OUTRO_UID, s.motoristaUid)
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

    // Sempre abaixo de Mensagem (menos em solicitação cancelada). Cinza até o
    // motorista usar o "Enviar Minha Localização" (só depois do aceite);
    // depois abre o mapa e mostra o horário do envio, pra saber se é recente.
    private fun mostrarLocalizacaoMotorista(b: ItemSolicitacaoBinding, s: Solicitacao, cancelada: Boolean) {
        if (cancelada) {
            b.containerLocalizacaoMotorista.visibility = View.GONE
            return
        }
        b.containerLocalizacaoMotorista.visibility = View.VISIBLE
        val lat = s.latitudeCompartilhada
        val lng = s.longitudeCompartilhada
        // Bolinha "1" enquanto o prestador não abriu a localização nova.
        BadgeUtil.mostrar(b.badgeLocalizacaoMotorista, if (s.localizacaoNaoVistaPrestador && lat != null) 1 else 0)
        if (lat == null || lng == null) {
            b.btnLocalizacaoMotorista.text = "📍 Localização do Motorista"
            b.btnLocalizacaoMotorista.backgroundTintList = ColorStateList.valueOf(0xFF9E9E9E.toInt())
            b.btnLocalizacaoMotorista.setOnClickListener {
                val aviso = if (s.status == ACEITO) "O motorista ainda não enviou a localização. Peça pelo chat."
                else "O motorista poderá enviar a localização depois que você aceitar a solicitação."
                Toast.makeText(b.root.context, aviso, Toast.LENGTH_LONG).show()
            }
            return
        }
        b.btnLocalizacaoMotorista.backgroundTintList = ColorStateList.valueOf(0xFF00897B.toInt())
        // Horário do envio na 2ª linha, pra caber no botão.
        b.btnLocalizacaoMotorista.text = s.localizacaoCompartilhadaEm
            ?.let { "📍 Localização do Motorista\nenviada em ${SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(it)}" }
            ?: "📍 Localização do Motorista"
        b.btnLocalizacaoMotorista.setOnClickListener {
            val context = b.root.context
            if (s.localizacaoNaoVistaPrestador) {
                s.localizacaoNaoVistaPrestador = false
                b.badgeLocalizacaoMotorista.visibility = View.GONE
                aoVerLocalizacao(s)
            }
            val link = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
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
