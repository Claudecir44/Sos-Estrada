package com.cjstudio.sosestrada;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PrestadorAdapter extends RecyclerView.Adapter<PrestadorAdapter.ViewHolder> {

    private List<Prestador> prestadores;
    private List<Prestador> prestadoresFull;
    private Context context;
    private OnSolicitarServicoListener solicitarListener;
    private OnItemLongClickListener longClickListener;

    public interface OnSolicitarServicoListener {
        void onSolicitarServico(Prestador prestador);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Prestador prestador);
    }

    public PrestadorAdapter(List<Prestador> prestadores, Context context,
                            OnSolicitarServicoListener solicitarListener,
                            OnItemLongClickListener longClickListener) {
        this.prestadores = prestadores;
        this.prestadoresFull = new ArrayList<>(prestadores);
        this.context = context;
        this.solicitarListener = solicitarListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_prestador_socorro, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Prestador p = prestadores.get(position);

        holder.tvNome.setText(p.getNome());
        holder.tvServico.setText("🔧 " + p.getServico());

        String endereco = p.getEnderecoCompleto();
        if (TextUtils.isEmpty(endereco) || endereco.equals(" ")) {
            holder.tvLocalizacao.setText("📍 Endereço não informado");
        } else {
            holder.tvLocalizacao.setText("📍 " + endereco);
        }

        holder.tvTelefone.setText("📞 " + p.getTelefone());

        if (p.getDistancia() > 0) {
            String distanciaStr;
            if (p.getDistancia() < 1) {
                distanciaStr = String.format("%.0f metros", p.getDistancia() * 1000);
            } else {
                distanciaStr = String.format("%.1f km", p.getDistancia());
            }
            holder.tvDistancia.setText("📏 Distância: " + distanciaStr);
        } else {
            holder.tvDistancia.setText("📏 Distância: não disponível");
        }

        if (p.getLogo() != null && !p.getLogo().isEmpty()) {
            Glide.with(context)
                    .load(p.getLogo())
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .into(holder.ivLogo);
        } else {
            holder.ivLogo.setImageResource(R.drawable.ic_placeholder_logo);
        }

        // Exibir status da solicitação
        String status = p.getStatusSolicitacao();
        if (!TextUtils.isEmpty(status)) {
            holder.tvStatusSolicitacao.setVisibility(View.VISIBLE);
            if (status.equals("pendente")) {
                holder.tvStatusSolicitacao.setText("⏳ Aguardando resposta do prestador...");
                holder.tvStatusSolicitacao.setBackgroundColor(0xFFFFF3E0); // laranja claro
                holder.btnSolicitar.setVisibility(View.GONE);
            } else if (status.equals("aceito")) {
                holder.tvStatusSolicitacao.setText("✅ Prestador aceitou e está a caminho!");
                holder.tvStatusSolicitacao.setBackgroundColor(0xFFE8F5E9); // verde claro
                holder.btnSolicitar.setVisibility(View.GONE);
            } else if (status.equals("recusado")) {
                holder.tvStatusSolicitacao.setText("❌ Prestador recusou. Procure outro!");
                holder.tvStatusSolicitacao.setBackgroundColor(0xFFFFEBEE); // vermelho claro
                holder.btnSolicitar.setVisibility(View.GONE);
            }
        } else {
            holder.tvStatusSolicitacao.setVisibility(View.GONE);
            holder.btnSolicitar.setVisibility(View.VISIBLE);
        }

        // Botão de mensagem: só aparece quando já existe uma solicitação com este prestador
        if (!TextUtils.isEmpty(p.getSolicitacaoId())) {
            holder.btnMensagem.setVisibility(View.VISIBLE);
            if (p.getNaoLidasMotorista() > 0) {
                holder.btnMensagem.setText("💬 Mensagem (" + p.getNaoLidasMotorista() + " nova"
                        + (p.getNaoLidasMotorista() > 1 ? "s" : "") + ")");
                holder.btnMensagem.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFD32F2F));
            } else {
                holder.btnMensagem.setText("💬 Mensagem");
                holder.btnMensagem.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF9C27B0));
            }
            holder.btnMensagem.setOnClickListener(v -> {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, p.getSolicitacaoId());
                intent.putExtra(ChatActivity.EXTRA_MEU_TIPO, "motorista");
                intent.putExtra(ChatActivity.EXTRA_TITULO, p.getNome());
                context.startActivity(intent);
            });
        } else {
            holder.btnMensagem.setVisibility(View.GONE);
        }

        // Long press para excluir solicitação (apenas se houver status)
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null && !TextUtils.isEmpty(p.getStatusSolicitacao())) {
                longClickListener.onItemLongClick(p);
                return true;
            }
            return false;
        });

        holder.btnChamar.setOnClickListener(v -> {
            String telefone = p.getTelefone().replaceAll("[^0-9]", "");
            if (!telefone.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + telefone));
                context.startActivity(intent);
            } else {
                abrirWhatsApp(p.getTelefone());
            }
        });

        holder.btnLocalizar.setOnClickListener(v -> {
            String enderecoLocalizar = p.getEnderecoCompleto();
            if (!TextUtils.isEmpty(enderecoLocalizar)
                    && !enderecoLocalizar.equals(" ")
                    && !enderecoLocalizar.equals("Endereço não informado")) {
                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(enderecoLocalizar));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(mapIntent);
                } else {
                    Intent fallback = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://maps.google.com/?q=" + Uri.encode(enderecoLocalizar)));
                    context.startActivity(fallback);
                }
            } else {
                Toast.makeText(context, "Endereço não disponível para localização", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnSolicitar.setOnClickListener(v -> {
            if (solicitarListener != null) {
                solicitarListener.onSolicitarServico(p);
            } else {
                Toast.makeText(context, "Erro ao solicitar serviço", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void abrirWhatsApp(String telefone) {
        String numero = telefone.replaceAll("[^0-9]", "");
        if (numero.length() >= 10) {
            if (!numero.startsWith("55")) {
                numero = "55" + numero;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + numero));
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "WhatsApp não instalado ou número inválido", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "Número inválido para WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return prestadores.size();
    }

    public void filter(String texto) {
        if (TextUtils.isEmpty(texto)) {
            prestadores = new ArrayList<>(prestadoresFull);
        } else {
            String lowerCase = texto.toLowerCase().trim();
            List<Prestador> filtered = new ArrayList<>();
            for (Prestador p : prestadoresFull) {
                String endereco = p.getEnderecoCompleto();
                if (endereco.equals("Endereço não informado")) {
                    endereco = "";
                }
                if (p.getNome().toLowerCase().contains(lowerCase) ||
                        p.getServico().toLowerCase().contains(lowerCase) ||
                        endereco.toLowerCase().contains(lowerCase)) {
                    filtered.add(p);
                }
            }
            prestadores = filtered;
        }
        notifyDataSetChanged();
    }

    public void updateList(List<Prestador> newList) {
        this.prestadores = newList;
        this.prestadoresFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivLogo;
        TextView tvNome, tvServico, tvDistancia, tvLocalizacao, tvTelefone, tvStatusSolicitacao;
        Button btnChamar, btnLocalizar, btnSolicitar, btnMensagem;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivLogo = itemView.findViewById(R.id.ivLogoPrestador);
            tvNome = itemView.findViewById(R.id.tvNomePrestador);
            tvServico = itemView.findViewById(R.id.tvServicoPrestador);
            tvDistancia = itemView.findViewById(R.id.tvDistanciaPrestador);
            tvLocalizacao = itemView.findViewById(R.id.tvLocalizacaoPrestador);
            tvTelefone = itemView.findViewById(R.id.tvTelefonePrestador);
            tvStatusSolicitacao = itemView.findViewById(R.id.tvStatusSolicitacao);
            btnChamar = itemView.findViewById(R.id.btnChamar);
            btnLocalizar = itemView.findViewById(R.id.btnLocalizar);
            btnSolicitar = itemView.findViewById(R.id.btnSolicitar);
            btnMensagem = itemView.findViewById(R.id.btnMensagem);
        }
    }
}