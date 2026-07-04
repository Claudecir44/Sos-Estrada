package com.cjstudio.sosestrada;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class SolicitacaoAdapter extends RecyclerView.Adapter<SolicitacaoAdapter.ViewHolder> {

    private List<Solicitacao> solicitacoes;
    private Context context;
    private FirebaseFirestore db;

    public SolicitacaoAdapter(List<Solicitacao> solicitacoes, Context context) {
        this.solicitacoes = solicitacoes;
        this.context = context;
        db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_solicitacao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Solicitacao s = solicitacoes.get(position);

        holder.tvMotoristaNome.setText("Motorista: " + s.getMotoristaNome());
        holder.tvMotoristaVeiculo.setText("🚗 Veículo: " + s.getMotoristaVeiculo());
        holder.tvMotoristaPlaca.setText("🔢 Placa: " + s.getMotoristaPlaca());
        holder.tvMotoristaTelefone.setText("📞 Telefone: " + s.getMotoristaTelefone());
        holder.tvMotoristaEndereco.setText("📍 Endereço: " + s.getEnderecoMotorista());

        // Oculta os botões se já estiver respondida
        if ("aceito".equals(s.getStatus()) || "recusado".equals(s.getStatus())) {
            holder.btnAceitar.setVisibility(View.GONE);
            holder.btnRecusar.setVisibility(View.GONE);
        } else {
            holder.btnAceitar.setVisibility(View.VISIBLE);
            holder.btnRecusar.setVisibility(View.VISIBLE);
        }

        holder.btnAceitar.setOnClickListener(v -> {
            atualizarStatus(s.getId(), "aceito");
        });

        holder.btnRecusar.setOnClickListener(v -> {
            atualizarStatus(s.getId(), "recusado");
        });
    }

    private void atualizarStatus(String id, String status) {
        db.collection("solicitacoes").document(id)
                .update("status", status)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Solicitação " + status + "!", Toast.LENGTH_SHORT).show();
                    // Atualiza a lista localmente
                    for (Solicitacao s : solicitacoes) {
                        if (s.getId().equals(id)) {
                            s.setStatus(status);
                            break;
                        }
                    }
                    notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Erro ao atualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public int getItemCount() {
        return solicitacoes.size();
    }

    public void updateList(List<Solicitacao> newList) {
        this.solicitacoes = newList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMotoristaNome, tvMotoristaVeiculo, tvMotoristaPlaca, tvMotoristaTelefone, tvMotoristaEndereco;
        Button btnAceitar, btnRecusar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMotoristaNome = itemView.findViewById(R.id.tvMotoristaNome);
            tvMotoristaVeiculo = itemView.findViewById(R.id.tvMotoristaVeiculo);
            tvMotoristaPlaca = itemView.findViewById(R.id.tvMotoristaPlaca);
            tvMotoristaTelefone = itemView.findViewById(R.id.tvMotoristaTelefone);
            tvMotoristaEndereco = itemView.findViewById(R.id.tvMotoristaEndereco);
            btnAceitar = itemView.findViewById(R.id.btnAceitar);
            btnRecusar = itemView.findViewById(R.id.btnRecusar);
        }
    }
}