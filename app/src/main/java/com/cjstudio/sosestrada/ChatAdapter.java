package com.cjstudio.sosestrada;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TIPO_ENVIADA = 1;
    private static final int TIPO_RECEBIDA = 2;

    private final List<Mensagem> mensagens;
    private final Context context;
    private final String meuTipo; // "motorista", "prestador", ou null (visão do admin)
    private final SimpleDateFormat horaFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(List<Mensagem> mensagens, Context context, String meuTipo) {
        this.mensagens = mensagens;
        this.context = context;
        this.meuTipo = meuTipo;
    }

    @Override
    public int getItemViewType(int position) {
        Mensagem m = mensagens.get(position);
        // Admin (meuTipo nulo) visualiza com o prestador sempre à direita, só para diferenciar os lados.
        String referencia = meuTipo != null ? meuTipo : "prestador";
        return referencia.equals(m.getRemetenteTipo()) ? TIPO_ENVIADA : TIPO_RECEBIDA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TIPO_ENVIADA) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_mensagem_enviada, parent, false);
            return new EnviadaViewHolder(v);
        } else {
            View v = LayoutInflater.from(context).inflate(R.layout.item_mensagem_recebida, parent, false);
            return new RecebidaViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Mensagem m = mensagens.get(position);

        String texto = m.getTexto();
        String imagemUrl = m.getImagemUrl();
        String hora = m.getTimestamp() != null ? horaFormat.format(m.getTimestamp()) : "";

        if (holder instanceof EnviadaViewHolder) {
            EnviadaViewHolder h = (EnviadaViewHolder) holder;
            bindTextoEImagem(h.tvTexto, h.ivImagem, texto, imagemUrl);
            h.tvHora.setText(hora);
            h.tvLida.setText(m.isLida() ? "✓✓" : "✓");
            h.tvLida.setTextColor(m.isLida() ? 0xFF4FC3F7 : 0xFF757575);
        } else if (holder instanceof RecebidaViewHolder) {
            RecebidaViewHolder h = (RecebidaViewHolder) holder;
            bindTextoEImagem(h.tvTexto, h.ivImagem, texto, imagemUrl);
            h.tvHora.setText(hora);
            if (meuTipo == null) {
                h.tvRemetente.setVisibility(View.VISIBLE);
                h.tvRemetente.setText("motorista".equals(m.getRemetenteTipo()) ? "Motorista" : "Prestador");
            } else {
                h.tvRemetente.setVisibility(View.GONE);
            }
        }
    }

    private void bindTextoEImagem(TextView tvTexto, ImageView ivImagem, String texto, String imagemUrl) {
        if (!TextUtils.isEmpty(imagemUrl)) {
            ivImagem.setVisibility(View.VISIBLE);
            Glide.with(context).load(imagemUrl).into(ivImagem);
        } else {
            ivImagem.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(texto)) {
            tvTexto.setVisibility(View.VISIBLE);
            tvTexto.setText(texto);
        } else {
            tvTexto.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mensagens.size();
    }

    static class EnviadaViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImagem;
        TextView tvTexto, tvHora, tvLida;

        EnviadaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImagem = itemView.findViewById(R.id.ivImagemMensagem);
            tvTexto = itemView.findViewById(R.id.tvTextoMensagem);
            tvHora = itemView.findViewById(R.id.tvHoraMensagem);
            tvLida = itemView.findViewById(R.id.tvLidaMensagem);
        }
    }

    static class RecebidaViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImagem;
        TextView tvTexto, tvHora, tvRemetente;

        RecebidaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImagem = itemView.findViewById(R.id.ivImagemMensagem);
            tvTexto = itemView.findViewById(R.id.tvTextoMensagem);
            tvHora = itemView.findViewById(R.id.tvHoraMensagem);
            tvRemetente = itemView.findViewById(R.id.tvRemetenteMensagem);
        }
    }
}
