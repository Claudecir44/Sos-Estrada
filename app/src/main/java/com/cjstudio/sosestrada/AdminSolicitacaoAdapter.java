package com.cjstudio.sosestrada;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AdminSolicitacaoAdapter extends RecyclerView.Adapter<AdminSolicitacaoAdapter.ViewHolder> {

    private List<Solicitacao> solicitacoes;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AdminSolicitacaoAdapter(List<Solicitacao> solicitacoes) {
        this.solicitacoes = solicitacoes;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_solicitacao, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Solicitacao s = solicitacoes.get(position);

        holder.tvMotoristaNome.setText("Motorista: " + s.getMotoristaNome());
        holder.tvMotoristaTelefone.setText("📞 " + s.getMotoristaTelefone());
        holder.tvMotoristaVeiculo.setText("🚗 " + s.getMotoristaVeiculo());
        holder.tvMotoristaPlaca.setText("🔢 " + s.getMotoristaPlaca());
        holder.tvMotoristaEndereco.setText("📍 " + s.getEnderecoMotorista());

        holder.tvPrestadorNome.setText("Prestador: " + s.getPrestadorNome());

        holder.tvStatus.setText("Status: " + s.getStatus());
        // Cores
        switch (s.getStatus()) {
            case "pendente":
                holder.tvStatus.setTextColor(0xFFFF9800);
                break;
            case "aceito":
                holder.tvStatus.setTextColor(0xFF4CAF50);
                break;
            case "recusado":
                holder.tvStatus.setTextColor(0xFFD32F2F);
                break;
            case "cancelado":
                holder.tvStatus.setTextColor(0xFF9E9E9E);
                break;
            default:
                holder.tvStatus.setTextColor(0xFF333333);
        }

        if (s.getTimestamp() != null) {
            holder.tvDataHora.setText("Data/Hora: " + dateFormat.format(s.getTimestamp()));
        } else {
            holder.tvDataHora.setText("Data/Hora: não informada");
        }

        holder.btnVerMensagens.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, s.getId());
            intent.putExtra(ChatActivity.EXTRA_READ_ONLY, true);
            intent.putExtra(ChatActivity.EXTRA_TITULO, s.getMotoristaNome() + " ↔ " + s.getPrestadorNome());
            v.getContext().startActivity(intent);
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
        TextView tvMotoristaNome, tvMotoristaTelefone, tvMotoristaVeiculo, tvMotoristaPlaca, tvMotoristaEndereco;
        TextView tvPrestadorNome, tvStatus, tvDataHora;
        Button btnVerMensagens;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMotoristaNome = itemView.findViewById(R.id.tvMotoristaNomeAdmin);
            tvMotoristaTelefone = itemView.findViewById(R.id.tvMotoristaTelefoneAdmin);
            tvMotoristaVeiculo = itemView.findViewById(R.id.tvMotoristaVeiculoAdmin);
            tvMotoristaPlaca = itemView.findViewById(R.id.tvMotoristaPlacaAdmin);
            tvMotoristaEndereco = itemView.findViewById(R.id.tvMotoristaEnderecoAdmin);
            tvPrestadorNome = itemView.findViewById(R.id.tvPrestadorNomeAdmin);
            tvStatus = itemView.findViewById(R.id.tvStatusAdmin);
            tvDataHora = itemView.findViewById(R.id.tvDataHoraAdmin);
            btnVerMensagens = itemView.findViewById(R.id.btnVerMensagensAdmin);
        }
    }
}