package com.cjstudio.sosestrada

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cjstudio.sosestrada.databinding.ItemMotoristaAdminBinding

class MotoristaAdminAdapter : RecyclerView.Adapter<MotoristaAdminAdapter.ViewHolder>() {

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
            tvNomeMotorista.text = "Nome: ${m.nome}"
            tvTelefoneMotorista.text = "Telefone: ${m.telefone}"
            tvEmailMotorista.text = "E-mail: ${m.email}"
            tvVeiculoMotorista.text = "Veículo: ${m.veiculo}"
            tvPlacaMotorista.text = "Placa: ${m.placa}"
            tvCorMotorista.text = "Cor: ${m.cor}"
            tvUidMotorista.text = "ID: ${m.uid}"
        }
    }
}
