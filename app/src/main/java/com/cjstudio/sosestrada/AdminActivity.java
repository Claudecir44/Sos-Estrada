package com.cjstudio.sosestrada;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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

public class AdminActivity extends AppCompatActivity {

    private Button btnMotoristas, btnPrestadores;
    private RecyclerView rvMotoristas, rvPrestadores;
    private ProgressBar progressBar;
    private TextView tvEmptyMotoristas, tvEmptyPrestadores, tvMensagemInicial;
    private FirebaseFirestore db;

    private MotoristaAdminAdapter motoristaAdapter;
    private PrestadorAdminAdapter prestadorAdapter;

    private List<Motorista> motoristaList = new ArrayList<>();
    private List<Prestador> prestadorList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        db = FirebaseFirestore.getInstance();

        btnMotoristas = findViewById(R.id.btnMotoristas);
        btnPrestadores = findViewById(R.id.btnPrestadores);
        rvMotoristas = findViewById(R.id.rvMotoristas);
        rvPrestadores = findViewById(R.id.rvPrestadores);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyMotoristas = findViewById(R.id.tvEmptyMotoristas);
        tvEmptyPrestadores = findViewById(R.id.tvEmptyPrestadores);
        tvMensagemInicial = findViewById(R.id.tvMensagemInicial);

        rvMotoristas.setLayoutManager(new LinearLayoutManager(this));
        rvPrestadores.setLayoutManager(new LinearLayoutManager(this));

        motoristaAdapter = new MotoristaAdminAdapter(motoristaList);
        prestadorAdapter = new PrestadorAdminAdapter(prestadorList);

        rvMotoristas.setAdapter(motoristaAdapter);
        rvPrestadores.setAdapter(prestadorAdapter);

        // Estado inicial: esconde listas e mostra mensagem inicial
        showMensagemInicial();

        btnMotoristas.setOnClickListener(v -> {
            showMotoristas();
            carregarMotoristas();
        });

        btnPrestadores.setOnClickListener(v -> {
            showPrestadores();
            carregarPrestadores();
        });

        // Adicione no onCreate:
        Button btnVerSolicitacoes = findViewById(R.id.btnVerSolicitacoes);
        btnVerSolicitacoes.setOnClickListener(v -> {
            startActivity(new Intent(AdminActivity.this, AdminSolicitacoesActivity.class));
        });
    }



    private void showMensagemInicial() {
        rvMotoristas.setVisibility(View.GONE);
        rvPrestadores.setVisibility(View.GONE);
        tvEmptyMotoristas.setVisibility(View.GONE);
        tvEmptyPrestadores.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        tvMensagemInicial.setVisibility(View.VISIBLE);

        btnMotoristas.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
        btnPrestadores.setBackgroundTintList(getColorStateList(android.R.color.darker_gray));
    }

    private void showMotoristas() {
        rvMotoristas.setVisibility(View.VISIBLE);
        rvPrestadores.setVisibility(View.GONE);
        tvEmptyMotoristas.setVisibility(motoristaList.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmptyPrestadores.setVisibility(View.GONE);
        tvMensagemInicial.setVisibility(View.GONE);

        btnMotoristas.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
        btnPrestadores.setBackgroundTintList(getColorStateList(android.R.color.darker_gray));
    }

    private void showPrestadores() {
        rvMotoristas.setVisibility(View.GONE);
        rvPrestadores.setVisibility(View.VISIBLE);
        tvEmptyMotoristas.setVisibility(View.GONE);
        tvEmptyPrestadores.setVisibility(prestadorList.isEmpty() ? View.VISIBLE : View.GONE);
        tvMensagemInicial.setVisibility(View.GONE);

        btnPrestadores.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
        btnMotoristas.setBackgroundTintList(getColorStateList(android.R.color.darker_gray));
    }

    private void carregarMotoristas() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("motoristas")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        motoristaList.clear();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Motorista m = doc.toObject(Motorista.class);
                            motoristaList.add(m);
                        }
                        motoristaAdapter.notifyDataSetChanged();
                        if (motoristaList.isEmpty()) {
                            tvEmptyMotoristas.setVisibility(View.VISIBLE);
                        } else {
                            tvEmptyMotoristas.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this, "Erro ao carregar motoristas: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void carregarPrestadores() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("prestadores")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        prestadorList.clear();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Prestador p = doc.toObject(Prestador.class);
                            prestadorList.add(p);
                        }
                        prestadorAdapter.notifyDataSetChanged();
                        if (prestadorList.isEmpty()) {
                            tvEmptyPrestadores.setVisibility(View.VISIBLE);
                        } else {
                            tvEmptyPrestadores.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this, "Erro ao carregar prestadores: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}