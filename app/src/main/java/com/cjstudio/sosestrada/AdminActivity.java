package com.cjstudio.sosestrada;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends AppCompatActivity {

    // Credenciais fixas para acesso administrativo
    private static final String ADMIN_USER = "Programador";
    private static final String ADMIN_PASSWORD = "SENHA_REMOVIDA";

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

        // Se já existe uma sessão válida (login anterior), pula a tela de senha
        // e mantém o admin logado entre uma abertura e outra do app.
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            inicializarPainel();
        } else {
            mostrarDialogoAutenticacao();
        }
    }

    /**
     * Exibe um diálogo solicitando usuário e senha antes de liberar o painel.
     * Cancelar ou errar a senha fecha a tela (não há conteúdo sem autenticar).
     */
    private void mostrarDialogoAutenticacao() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("🔐 Acesso Administrativo");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_login, null);
        final EditText edtUsuario = view.findViewById(R.id.edtUsuario);
        final EditText edtSenha = view.findViewById(R.id.edtSenha);
        builder.setView(view);

        builder.setPositiveButton("Entrar", (dialog, which) -> {
            String usuario = edtUsuario.getText().toString().trim();
            String senha = edtSenha.getText().toString().trim();

            if (TextUtils.isEmpty(usuario) || TextUtils.isEmpty(senha)) {
                Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            if (usuario.equals(ADMIN_USER) && senha.equals(ADMIN_PASSWORD)) {
                autenticarEIniciarPainel();
            } else {
                Toast.makeText(this, "❌ Usuário ou senha incorretos.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> finish());
        builder.show();
    }

    /**
     * As regras do Firestore exigem request.auth != null. Como o login do admin
     * é só usuário/senha fixos (sem Firebase Auth), autentica anonimamente antes
     * de liberar o painel, para as leituras/gravações não serem bloqueadas.
     */
    private void autenticarEIniciarPainel() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            inicializarPainel();
            return;
        }
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(result -> inicializarPainel())
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Erro ao autenticar admin: " + e.getMessage()
                                    + "\nHabilite o login Anônimo no Firebase Console (Authentication > Sign-in method).",
                            Toast.LENGTH_LONG).show();
                    inicializarPainel();
                });
    }

    private void inicializarPainel() {
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

        Button btnVerMensagensAdmin = findViewById(R.id.btnVerMensagensAdmin);
        btnVerMensagensAdmin.setOnClickListener(v -> {
            startActivity(new Intent(AdminActivity.this, AdminMensagensActivity.class));
        });

        Button btnSairAdmin = findViewById(R.id.btnSairAdmin);
        btnSairAdmin.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            finish();
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
                        Toast.makeText(this, "Erro ao carregar motoristas: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        tvEmptyMotoristas.setText("Erro ao carregar motoristas.\n" + task.getException().getMessage());
                        tvEmptyMotoristas.setVisibility(View.VISIBLE);
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
                        Toast.makeText(this, "Erro ao carregar prestadores: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        tvEmptyPrestadores.setText("Erro ao carregar prestadores.\n" + task.getException().getMessage());
                        tvEmptyPrestadores.setVisibility(View.VISIBLE);
                    }
                });
    }
}