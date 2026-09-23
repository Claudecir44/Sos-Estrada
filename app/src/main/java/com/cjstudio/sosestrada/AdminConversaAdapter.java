package com.cjstudio.sosestrada;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AdminConversaAdapter extends RecyclerView.Adapter<AdminConversaAdapter.ViewHolder> {

    public interface OnConversaClickListener {
        void onConversaClick(Solicitacao solicitacao);
    }

    private List<Solicitacao> solicitacoes;
    private final OnConversaClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public AdminConversaAdapter(List<Solicitacao> solicitacoes, OnConversaClickListener listener) {
        this.solicitacoes = solicitacoes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_conversa, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Solicitacao s = solicitacoes.get(position);

        holder.tvMotorista.setText("🚗 Motorista: " + s.getMotoristaNome());
        holder.tvPrestador.setText("🔧 Prestador: " + s.getPrestadorNome());
        holder.tvStatus.setText("Status: " + s.getStatus());

        if (s.getTimestamp() != null) {
            holder.tvDataHora.setText("Data/Hora: " + dateFormat.format(s.getTimestamp()));
        } else {
            holder.tvDataHora.setText("Data/Hora: não informada");
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversaClick(s);
            }
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
        TextView tvMotorista, tvPrestador, tvStatus, tvDataHora;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMotorista = itemView.findViewById(R.id.tvConversaMotorista);
            tvPrestador = itemView.findViewById(R.id.tvConversaPrestador);
            tvStatus = itemView.findViewById(R.id.tvConversaStatus);
            tvDataHora = itemView.findViewById(R.id.tvConversaDataHora);
        }
    }
}
