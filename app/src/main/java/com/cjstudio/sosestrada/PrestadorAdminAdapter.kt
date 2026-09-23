package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cjstudio.sosestrada.databinding.ItemPrestadorAdminBinding

class PrestadorAdminAdapter : RecyclerView.Adapter<PrestadorAdminAdapter.ViewHolder>() {

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
            tvUidPrestador.text = "ID ${p.uid.orEmpty()}"
            if (!p.logo.isNullOrEmpty()) {
                Glide.with(root).load(p.logo).placeholder(R.drawable.ic_placeholder_logo).into(ivLogoPrestador)
            } else {
                ivLogoPrestador.setImageResource(R.drawable.ic_placeholder_logo)
            }
        }
    }
}
