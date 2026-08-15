package com.cjstudio.sosestrada;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
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
    private OnAcaoListener listener;

    public interface OnAcaoListener {
        void onStatusChanged(String id, String novoStatus);
        void onExcluirPermanente(String id);
    }

    public SolicitacaoAdapter(List<Solicitacao> solicitacoes, Context context, OnAcaoListener listener) {
        this.solicitacoes = solicitacoes;
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.listener = listener;
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
        String status = s.getStatus();

        holder.tvMotoristaNome.setText("Motorista: " + s.getMotoristaNome());
        holder.tvMotoristaVeiculo.setText("🚗 Veículo: " + s.getMotoristaVeiculo());
        holder.tvMotoristaPlaca.setText("🔢 Placa: " + s.getMotoristaPlaca());
        holder.tvMotoristaTelefone.setText("📞 Telefone: " + s.getMotoristaTelefone());
        holder.tvMotoristaEndereco.setText("📍 Endereço: " + s.getEnderecoMotorista());

        // Se status for "cancelado", mostra mensagem e esconde os botões
        if ("cancelado".equals(status)) {
            holder.tvCancelamento.setVisibility(View.VISIBLE);
            holder.layoutBotoes.setVisibility(View.GONE);
            holder.btnMensagem.setVisibility(View.GONE);
        } else {
            holder.tvCancelamento.setVisibility(View.GONE);
            holder.layoutBotoes.setVisibility(View.VISIBLE);
            holder.btnMensagem.setVisibility(View.VISIBLE);
        }

        if (s.getNaoLidasPrestador() > 0) {
            holder.btnMensagem.setText("💬 Mensagem (" + s.getNaoLidasPrestador() + " nova"
                    + (s.getNaoLidasPrestador() > 1 ? "s" : "") + ")");
            holder.btnMensagem.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFD32F2F));
        } else {
            holder.btnMensagem.setText("💬 Mensagem");
            holder.btnMensagem.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF9C27B0));
        }

        // Se já foi aceito ou recusado, esconde os botões (mantém comportamento anterior)
        if ("aceito".equals(status) || "recusado".equals(status)) {
            holder.layoutBotoes.setVisibility(View.GONE);
        }

        // Clique em Aceitar → exibe diálogo com checkbox
        holder.btnAceitar.setOnClickListener(v -> {
            if ("pendente".equals(status)) {
                mostrarDialogoConfirmacao(s);
            } else {
                Toast.makeText(context, "Esta solicitação já foi respondida.", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnRecusar.setOnClickListener(v -> {
            if (listener != null && "pendente".equals(status)) {
                listener.onStatusChanged(s.getId(), "recusado");
            } else {
                Toast.makeText(context, "Esta solicitação já foi respondida.", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnMensagem.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, s.getId());
            intent.putExtra(ChatActivity.EXTRA_MEU_TIPO, "prestador");
            intent.putExtra(ChatActivity.EXTRA_TITULO, s.getMotoristaNome());
            context.startActivity(intent);
        });

        // Long press para excluir permanentemente (apenas se status for "cancelado")
        holder.itemView.setOnLongClickListener(v -> {
            if ("cancelado".equals(status)) {
                new AlertDialog.Builder(context)
                        .setTitle("Excluir permanentemente")
                        .setMessage("Deseja excluir esta solicitação cancelada?")
                        .setPositiveButton("Sim", (dialog, which) -> {
                            if (listener != null) {
                                listener.onExcluirPermanente(s.getId());
                            }
                        })
                        .setNegativeButton("Não", null)
                        .show();
                return true;
            }
            return false;
        });
    }

    private void mostrarDialogoConfirmacao(Solicitacao s) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Confirmar atendimento");

        // Mensagem + CheckBox
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_aceitar_solicitacao, null);
        CheckBox checkBox = view.findViewById(R.id.checkBoxConfirmacao);
        TextView tvMensagem = view.findViewById(R.id.tvMensagemConfirmacao);

        tvMensagem.setText("Ao aceitar você concorda que estará indo socorrer o motorista, e será enviada uma mensagem ao mesmo, confirmando seu deslocamento.");

        builder.setView(view);

        builder.setPositiveButton("Aceitar", (dialog, which) -> {
            if (checkBox.isChecked()) {
                if (listener != null) {
                    listener.onStatusChanged(s.getId(), "aceito");
                }
            } else {
                Toast.makeText(context, "Marque a checkbox para confirmar.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
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
        TextView tvMotoristaNome, tvMotoristaVeiculo, tvMotoristaPlaca, tvMotoristaTelefone, tvMotoristaEndereco, tvCancelamento;
        Button btnAceitar, btnRecusar, btnMensagem;
        LinearLayout layoutBotoes;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMotoristaNome = itemView.findViewById(R.id.tvMotoristaNome);
            tvMotoristaVeiculo = itemView.findViewById(R.id.tvMotoristaVeiculo);
            tvMotoristaPlaca = itemView.findViewById(R.id.tvMotoristaPlaca);
            tvMotoristaTelefone = itemView.findViewById(R.id.tvMotoristaTelefone);
            tvMotoristaEndereco = itemView.findViewById(R.id.tvMotoristaEndereco);
            tvCancelamento = itemView.findViewById(R.id.tvCancelamento);
            btnAceitar = itemView.findViewById(R.id.btnAceitar);
            btnRecusar = itemView.findViewById(R.id.btnRecusar);
            layoutBotoes = itemView.findViewById(R.id.layoutBotoes);
            btnMensagem = itemView.findViewById(R.id.btnMensagem);
        }
    }
}