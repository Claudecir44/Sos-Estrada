package com.cjstudio.sosestrada;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PrestadorAdminAdapter extends RecyclerView.Adapter<PrestadorAdminAdapter.ViewHolder> {

    private List<Prestador> prestadores;

    public PrestadorAdminAdapter(List<Prestador> prestadores) {
        this.prestadores = prestadores;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_prestador_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Prestador p = prestadores.get(position);
        holder.tvNome.setText("Nome: " + p.getNome());
        holder.tvCnpj.setText("CNPJ: " + p.getCnpj());
        holder.tvTelefone.setText("Telefone: " + p.getTelefone());
        holder.tvEmail.setText("E-mail: " + p.getEmail());
        holder.tvServico.setText("Serviço: " + p.getServico());

        // ✅ Usa o endereço completo
        String enderecoCompleto = p.getEnderecoCompleto();
        holder.tvEndereco.setText("Endereço: " + enderecoCompleto);

        // ✅ Localização simplificada (cidade + estado)
        String cidade = p.getCidade();
        String estado = p.getEstado();
        String localizacao = "Localização: ";
        if ((cidade != null && !cidade.isEmpty()) || (estado != null && !estado.isEmpty())) {
            localizacao += (cidade != null && !cidade.isEmpty() ? cidade : "") +
                    (estado != null && !estado.isEmpty() ? " - " + estado : "");
        } else {
            localizacao += "Não informada";
        }
        holder.tvLocalizacao.setText(localizacao);

        holder.tvPreco.setText("Preço: " + p.getPreco());
        holder.tvUid.setText("ID: " + p.getUid());

        if (p.getLogo() != null && !p.getLogo().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(p.getLogo())
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .into(holder.ivLogo);
        } else {
            holder.ivLogo.setImageResource(R.drawable.ic_placeholder_logo);
        }
    }

    @Override
    public int getItemCount() {
        return prestadores.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNome, tvCnpj, tvTelefone, tvEmail, tvServico, tvLocalizacao, tvEndereco, tvPreco, tvUid;
        ImageView ivLogo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNome = itemView.findViewById(R.id.tvNomePrestador);
            tvCnpj = itemView.findViewById(R.id.tvCnpjPrestador);
            tvTelefone = itemView.findViewById(R.id.tvTelefonePrestador);
            tvEmail = itemView.findViewById(R.id.tvEmailPrestador);
            tvServico = itemView.findViewById(R.id.tvServicoPrestador);
            tvLocalizacao = itemView.findViewById(R.id.tvLocalizacaoPrestador);
            tvEndereco = itemView.findViewById(R.id.tvEnderecoPrestador);
            tvPreco = itemView.findViewById(R.id.tvPrecoPrestador);
            tvUid = itemView.findViewById(R.id.tvUidPrestador);
            ivLogo = itemView.findViewById(R.id.ivLogoPrestador);
        }
    }
}