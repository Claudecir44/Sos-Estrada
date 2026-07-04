package com.cjstudio.sosestrada;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SolicitacoesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SolicitacaoAdapter adapter;
    private List<Solicitacao> solicitacaoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solicitacoes);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        recyclerView = findViewById(R.id.rvSolicitacoes);
        progressBar = findViewById(R.id.progressBarSolicitacoes);
        tvEmpty = findViewById(R.id.tvEmptySolicitacoes);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SolicitacaoAdapter(solicitacaoList, this);
        recyclerView.setAdapter(adapter);

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
                .whereEqualTo("status", "pendente")
                .orderBy("timestamp", Query.Direction.DESCENDING)
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
                        adapter.notifyDataSetChanged();
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