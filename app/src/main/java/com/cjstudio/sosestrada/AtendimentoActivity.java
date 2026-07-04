package com.cjstudio.sosestrada;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AtendimentoActivity extends AppCompatActivity implements SolicitacaoAdapter.OnAcaoListener {

    private RecyclerView rvAtendimento;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SolicitacaoAdapter adapter;
    private List<Solicitacao> solicitacaoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_atendimento);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        rvAtendimento = findViewById(R.id.rvAtendimento);
        progressBar = findViewById(R.id.progressBarAtendimento);
        tvEmpty = findViewById(R.id.tvEmptyAtendimento);

        rvAtendimento.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SolicitacaoAdapter(solicitacaoList, this, this);
        rvAtendimento.setAdapter(adapter);

        carregarSolicitacoes();
    }

    private void carregarSolicitacoes() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        db.collection("solicitacoes")
                .whereEqualTo("prestadorUid", user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        solicitacaoList.clear();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Solicitacao s = doc.toObject(Solicitacao.class);
                            s.setId(doc.getId());
                            solicitacaoList.add(s);
                        }
                        adapter.updateList(solicitacaoList);
                        if (solicitacaoList.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(this, "Erro ao carregar: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    // ---------- OnAcaoListener ----------
    @Override
    public void onStatusChanged(String id, String novoStatus) {
        db.collection("solicitacoes").document(id)
                .update("status", novoStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Status atualizado para: " + novoStatus, Toast.LENGTH_SHORT).show();
                    // Atualiza a lista local
                    for (Solicitacao s : solicitacaoList) {
                        if (s.getId().equals(id)) {
                            s.setStatus(novoStatus);
                            break;
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao atualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onExcluirPermanente(String id) {
        db.collection("solicitacoes").document(id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Solicitação excluída permanentemente.", Toast.LENGTH_SHORT).show();
                    // Remove da lista
                    solicitacaoList.removeIf(s -> s.getId().equals(id));
                    adapter.notifyDataSetChanged();
                    if (solicitacaoList.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}