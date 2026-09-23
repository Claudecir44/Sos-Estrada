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
            tvNomePrestador.text = "Nome: ${p.nome}"
            tvCnpjPrestador.text = "CNPJ: ${p.cnpj}"
            tvTelefonePrestador.text = "Telefone: ${p.telefone}"
            tvEmailPrestador.text = "E-mail: ${p.email}"
            tvServicoPrestador.text = "Serviço: ${p.servico}"
            tvEnderecoPrestador.text = "Endereço: ${p.enderecoCompleto}"
            // Localização resumida: cidade - estado.
            val cidadeEstado = listOfNotNull(p.cidade?.takeIf { it.isNotEmpty() }, p.estado?.takeIf { it.isNotEmpty() })
                .joinToString(" - ")
            tvLocalizacaoPrestador.text = "Localização: " + cidadeEstado.ifEmpty { "Não informada" }
            tvPrecoPrestador.text = "Preço: ${p.preco}"
            tvUidPrestador.text = "ID: ${p.uid}"
            if (!p.logo.isNullOrEmpty()) {
                Glide.with(root).load(p.logo).placeholder(R.drawable.ic_placeholder_logo).into(ivLogoPrestador)
            } else {
                ivLogoPrestador.setImageResource(R.drawable.ic_placeholder_logo)
            }
        }
    }
}
