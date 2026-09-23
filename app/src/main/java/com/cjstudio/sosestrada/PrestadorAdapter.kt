package com.cjstudio.sosestrada

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.ACEITO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.CANCELADO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.PENDENTE
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.RECUSADO
import com.cjstudio.sosestrada.databinding.ItemPrestadorSocorroBinding
import java.util.Locale

// Lista de prestadores na busca de socorro do motorista (SocorroActivity).
class PrestadorAdapter(
    private val aoSolicitar: (Prestador) -> Unit,
    private val aoSegurar: (Prestador) -> Unit
) : RecyclerView.Adapter<PrestadorAdapter.ViewHolder>() {

    private var todos: List<Prestador> = emptyList()
    private var visiveis: List<Prestador> = emptyList()
    private var filtroAtual = ""

    class ViewHolder(val binding: ItemPrestadorSocorroBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPrestadorSocorroBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = visiveis.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = visiveis[position]
        val b = holder.binding
        val context = b.root.context

        b.tvNomePrestador.text = p.nome
        b.tvServicoPrestador.text = "🔧 ${p.servico ?: ""}"
        b.tvLocalizacaoPrestador.text = "📍 ${p.enderecoCompleto}"
        b.tvTelefonePrestador.text = "📞 ${p.telefone ?: ""}"
        b.tvDistanciaPrestador.text = "📏 Distância: " + when {
            p.distancia <= 0 -> "não disponível"
            p.distancia < 1 -> String.format(Locale.getDefault(), "%.0f metros", p.distancia * 1000)
            else -> String.format(Locale.getDefault(), "%.1f km", p.distancia)
        }

        FotoUtil.mostrar(b.ivLogoPrestador, p.logo, R.drawable.ic_placeholder_logo)

        mostrarStatus(b, p.statusSolicitacao)
        mostrarBotaoMensagem(b, p)

        b.root.setOnLongClickListener {
            if (p.statusSolicitacao.isNullOrEmpty()) return@setOnLongClickListener false
            aoSegurar(p)
            true
        }
        b.btnChamar.setOnClickListener { chamar(context, p.telefone) }
        b.btnLocalizar.setOnClickListener { abrirNoMapa(context, p.enderecoCompleto) }
        b.btnSolicitar.setOnClickListener { aoSolicitar(p) }
    }

    // Todos os campos são definidos em todo bind (o ViewHolder é reaproveitado
    // entre itens — deixar um sem definir mostrava o estado de outro prestador).
    private fun mostrarStatus(b: ItemPrestadorSocorroBinding, status: String?) {
        val (texto, cor, podeSolicitar) = when (status) {
            PENDENTE -> Triple("⏳ Aguardando resposta do prestador...", 0xFFFFF3E0.toInt(), false)
            ACEITO -> Triple("✅ Prestador aceitou e está a caminho!", 0xFFE8F5E9.toInt(), false)
            RECUSADO -> Triple("❌ Prestador recusou. Procure outro!", 0xFFFFEBEE.toInt(), false)
            // Cancelada: pode pedir de novo; segurar o card oferece a exclusão.
            CANCELADO -> Triple("🚫 Solicitação cancelada (segure para excluir)", 0xFFEEEEEE.toInt(), true)
            else -> Triple(null, 0, true)
        }
        if (texto == null) {
            b.tvStatusSolicitacao.visibility = View.GONE
        } else {
            b.tvStatusSolicitacao.visibility = View.VISIBLE
            b.tvStatusSolicitacao.text = texto
            b.tvStatusSolicitacao.setBackgroundColor(cor)
        }
        b.btnSolicitar.visibility = if (podeSolicitar) View.VISIBLE else View.GONE
    }

    // Só aparece quando já existe uma solicitação com este prestador.
    private fun mostrarBotaoMensagem(b: ItemPrestadorSocorroBinding, p: Prestador) {
        val solicitacaoId = p.solicitacaoId
        if (solicitacaoId.isNullOrEmpty()) {
            b.btnMensagem.visibility = View.GONE
            return
        }
        b.btnMensagem.visibility = View.VISIBLE
        if (p.naoLidasMotorista > 0) {
            val plural = if (p.naoLidasMotorista > 1) "s" else ""
            b.btnMensagem.text = "💬 Mensagem (${p.naoLidasMotorista} nova$plural)"
            b.btnMensagem.backgroundTintList = ColorStateList.valueOf(0xFFD32F2F.toInt())
        } else {
            b.btnMensagem.text = "💬 Mensagem"
            b.btnMensagem.backgroundTintList = ColorStateList.valueOf(0xFF9C27B0.toInt())
        }
        b.btnMensagem.setOnClickListener {
            val context = b.root.context
            context.startActivity(
                Intent(context, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, solicitacaoId)
                    .putExtra(ChatActivity.EXTRA_MEU_TIPO, IChatRepository.MOTORISTA)
                    .putExtra(ChatActivity.EXTRA_TITULO, p.nome)
            )
        }
    }

    private fun chamar(context: android.content.Context, telefone: String?) {
        val numero = telefone.orEmpty().filter { it.isDigit() }
        if (numero.isNotEmpty()) {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))
        } else {
            Toast.makeText(context, "Telefone não informado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirNoMapa(context: android.content.Context, endereco: String) {
        if (endereco.isBlank() || endereco == "Endereço não informado") {
            Toast.makeText(context, "Endereço não disponível para localização", Toast.LENGTH_SHORT).show()
            return
        }
        val mapa = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(endereco)}"))
            .setPackage("com.google.android.apps.maps")
        if (mapa.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapa)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(endereco)}")))
        }
    }

    // Filtra por nome, serviço ou endereço (campos vazios não quebram a busca).
    fun filtrar(texto: String) {
        filtroAtual = texto.trim().lowercase()
        aplicarFiltro()
    }

    fun atualizarLista(novaLista: List<Prestador>) {
        todos = novaLista
        aplicarFiltro()
    }

    private fun aplicarFiltro() {
        visiveis = if (filtroAtual.isEmpty()) {
            todos
        } else {
            todos.filter { p ->
                val endereco = p.enderecoCompleto.takeIf { it != "Endereço não informado" }.orEmpty()
                listOf(p.nome, p.servico, endereco).any { it.orEmpty().lowercase().contains(filtroAtual) }
            }
        }
        notifyDataSetChanged()
    }
}
