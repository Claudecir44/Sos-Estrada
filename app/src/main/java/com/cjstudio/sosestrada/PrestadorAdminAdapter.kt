package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemPrestadorAdminBinding

// aoSegurar: segurar o cartão abre editar/bloquear/excluir (AdminActivity).
class PrestadorAdminAdapter(
    private val aoSegurar: (Prestador) -> Unit
) : RecyclerView.Adapter<PrestadorAdminAdapter.ViewHolder>() {

    private var prestadores: List<Prestador> = emptyList()

    class ViewHolder(val binding: ItemPrestadorAdminBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(novaLista: List<Prestador>) {
        prestadores = novaLista
        notifyDataSetChanged()
    }

    override fun getItemCount() = prestadores.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPrestadorAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = prestadores[position]
        with(holder.binding) {
            tvNomePrestador.text = p.nome ?: "Sem nome"
            tvServicoPrestador.text = "🔧 ${p.servico?.takeIf { it.isNotBlank() } ?: "Serviço não informado"}"
            tvTelefonePrestador.text = "📞 ${p.telefone ?: "Não informado"}"
            tvEmailPrestador.text = "✉️ ${p.email.orEmpty()}"
            tvEnderecoPrestador.text = "📍 ${p.enderecoCompleto}"
            tvCnpjPrestador.text = listOfNotNull(
                p.cnpj?.takeIf { it.isNotBlank() }?.let { "CNPJ $it" },
                p.preco?.takeIf { it.isNotBlank() }?.let { "💲 $it" }
            ).joinToString("  •  ").ifEmpty { "CNPJ não informado" }
            tvBloqueadoPrestador.visibility = if (p.bloqueado) View.VISIBLE else View.GONE
            root.setOnLongClickListener { aoSegurar(p); true }
            FotoUtil.mostrar(ivLogoPrestador, p.logo, R.drawable.ic_placeholder_logo)
        }
    }
}
