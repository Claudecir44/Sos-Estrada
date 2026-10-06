package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemAdminManifestacaoBinding

// Chip "📝 Sugestões" do painel: reclamações, sugestões e denúncias.
// Tocar abre responder/arquivar (AdminActivity.abrirManifestacao).
class ManifestacaoAdminAdapter(
    private val aoTocar: (Manifestacao) -> Unit
) : RecyclerView.Adapter<ManifestacaoAdminAdapter.ViewHolder>() {

    private var itens: List<Manifestacao> = emptyList()

    class ViewHolder(val binding: ItemAdminManifestacaoBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(novaLista: List<Manifestacao>) {
        itens = novaLista
        notifyDataSetChanged()
    }

    override fun getItemCount() = itens.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAdminManifestacaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val m = itens[position]
        with(holder.binding) {
            tvManifestacaoTipo.text = Manifestacao.rotuloTipo(m.tipo)
            tvManifestacaoAutor.text = listOfNotNull(m.nome, m.email).joinToString(" · ").ifEmpty { "Usuário" }
            if (m.tipo == Manifestacao.DENUNCIA && !m.denunciadoNome.isNullOrBlank()) {
                tvManifestacaoDenunciado.visibility = View.VISIBLE
                tvManifestacaoDenunciado.text = "Contra: ${m.denunciadoNome} — ${m.motivo.orEmpty()}"
            } else {
                tvManifestacaoDenunciado.visibility = View.GONE
            }
            tvManifestacaoMensagem.text = m.mensagem?.takeIf { it.isNotBlank() } ?: "(sem texto)"
            tvManifestacaoStatus.text = when (m.status) {
                Manifestacao.STATUS_RESPONDIDA -> "✅ Respondida"
                Manifestacao.STATUS_ARQUIVADA -> "🗄️ Arquivada"
                else -> "🆕 Nova"
            }
            tvManifestacaoData.text = dataHora(m.criadoEm)
            root.setOnClickListener { aoTocar(m) }
        }
    }
}
