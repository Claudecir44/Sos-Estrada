package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemMotoristaAdminBinding

// aoSegurar: segurar o cartão abre editar/bloquear/excluir (AdminActivity).
class MotoristaAdminAdapter(
    private val aoSegurar: (Motorista) -> Unit
) : RecyclerView.Adapter<MotoristaAdminAdapter.ViewHolder>() {

    private var motoristas: List<Motorista> = emptyList()

    class ViewHolder(val binding: ItemMotoristaAdminBinding) : RecyclerView.ViewHolder(binding.root)

    fun atualizarLista(novaLista: List<Motorista>) {
        motoristas = novaLista
        notifyDataSetChanged()
    }

    override fun getItemCount() = motoristas.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemMotoristaAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val m = motoristas[position]
        with(holder.binding) {
            FotoUtil.mostrar(ivFotoMotorista, m.foto)
            tvNomeMotorista.text = m.nome ?: "Sem nome"
            tvEmailMotorista.text = m.email.orEmpty()
            // "🚗 Onix • ABC1D23 • Prata" (só o que estiver preenchido).
            tvVeiculoMotorista.text = "🚗 " + listOfNotNull(m.veiculo, m.placa, m.cor)
                .filter { it.isNotBlank() }.joinToString(" • ").ifEmpty { "Veículo não informado" }
            tvTelefoneMotorista.text = "📞 ${m.telefone ?: "Não informado"}"
            tvBloqueadoMotorista.visibility = if (m.bloqueado) View.VISIBLE else View.GONE
            root.setOnLongClickListener { aoSegurar(m); true }
        }
    }
}
