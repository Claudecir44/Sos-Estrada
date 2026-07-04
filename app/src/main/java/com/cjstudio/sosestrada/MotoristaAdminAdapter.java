package com.cjstudio.sosestrada;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MotoristaAdminAdapter extends RecyclerView.Adapter<MotoristaAdminAdapter.ViewHolder> {

    private List<Motorista> motoristas;

    public MotoristaAdminAdapter(List<Motorista> motoristas) {
        this.motoristas = motoristas;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_motorista_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Motorista m = motoristas.get(position);
        holder.tvNome.setText("Nome: " + m.getNome());
        holder.tvTelefone.setText("Telefone: " + m.getTelefone());
        holder.tvEmail.setText("E-mail: " + m.getEmail());
        holder.tvVeiculo.setText("Veículo: " + m.getVeiculo());
        holder.tvPlaca.setText("Placa: " + m.getPlaca());
        holder.tvCor.setText("Cor: " + m.getCor());
        holder.tvUid.setText("ID: " + m.getUid());
    }

    @Override
    public int getItemCount() {
        return motoristas.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNome, tvTelefone, tvEmail, tvVeiculo, tvPlaca, tvCor, tvUid;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNome = itemView.findViewById(R.id.tvNomeMotorista);
            tvTelefone = itemView.findViewById(R.id.tvTelefoneMotorista);
            tvEmail = itemView.findViewById(R.id.tvEmailMotorista);
            tvVeiculo = itemView.findViewById(R.id.tvVeiculoMotorista);
            tvPlaca = itemView.findViewById(R.id.tvPlacaMotorista);
            tvCor = itemView.findViewById(R.id.tvCorMotorista);
            tvUid = itemView.findViewById(R.id.tvUidMotorista);
        }
    }
}