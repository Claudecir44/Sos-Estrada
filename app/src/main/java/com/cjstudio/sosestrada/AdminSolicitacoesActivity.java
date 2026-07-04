package com.cjstudio.sosestrada;

import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminSolicitacoesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private AdminSolicitacaoAdapter adapter;
    private List<Solicitacao> solicitacaoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_solicitacoes);

        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.rvAdminSolicitacoes);
        progressBar = findViewById(R.id.progressBarAdminSolicitacoes);
        tvEmpty = findViewById(R.id.tvEmptyAdminSolicitacoes);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminSolicitacaoAdapter(solicitacaoList);
        recyclerView.setAdapter(adapter);

        carregarSolicitacoes();
    }

    private void carregarSolicitacoes() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        db.collection("solicitacoes")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    if (task.isSuccessful()) {
                        solicitacaoList.clear();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Solicitacao s = doc.toObject(Solicitacao.class);
                            s.setId(doc.getId());
                            solicitacaoList.add(s);
                        }
                        adapter.updateList(solicitacaoList);
                        tvEmpty.setVisibility(solicitacaoList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
                    } else {
                        Toast.makeText(this, "Erro ao carregar solicitações: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        tvEmpty.setVisibility(TextView.VISIBLE);
                    }
                });
    }
}