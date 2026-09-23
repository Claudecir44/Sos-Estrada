package com.cjstudio.sosestrada;

import android.content.Intent;
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

public class AdminMensagensActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FirebaseFirestore db;
    private AdminConversaAdapter adapter;
    private final List<Solicitacao> solicitacaoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_mensagens);

        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.rvAdminMensagens);
        progressBar = findViewById(R.id.progressBarAdminMensagens);
        tvEmpty = findViewById(R.id.tvEmptyAdminMensagens);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminConversaAdapter(solicitacaoList, solicitacao -> {
            Intent intent = new Intent(AdminMensagensActivity.this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_SOLICITACAO_ID, solicitacao.getId());
            intent.putExtra(ChatActivity.EXTRA_READ_ONLY, true);
            intent.putExtra(ChatActivity.EXTRA_TITULO, solicitacao.getMotoristaNome() + " ↔ " + solicitacao.getPrestadorNome());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        carregarConversas();
    }

    private void carregarConversas() {
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
                        Toast.makeText(this, "Erro ao carregar conversas: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        tvEmpty.setVisibility(TextView.VISIBLE);
                    }
                });
    }
}
