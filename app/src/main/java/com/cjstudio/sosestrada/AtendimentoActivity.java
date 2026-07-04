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

public class AtendimentoActivity extends AppCompatActivity {

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
        adapter = new SolicitacaoAdapter(solicitacaoList, this);
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

        // ✅ Removido o orderBy() para evitar erro de índice.
        // Para ordenar, você pode criar o índice no Firebase Console usando o link:
        // https://console.firebase.google.com/v1/r/project/sos-estrada-dc55d/firestore/indexes?create_composite=...
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
                        // Ordenação local (opcional)
                        // solicitacaoList.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
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
}