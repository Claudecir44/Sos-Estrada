package com.cjstudio.sosestrada;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CadastroMotoristaActivity extends AppCompatActivity {

    private TextInputEditText edtNome, edtTelefone, edtEmail, edtSenha, edtVeiculo, edtPlaca, edtCor;
    private MaterialButton btnCadastrar;
    private TextView btnVoltar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isEditando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_motorista);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        edtNome = findViewById(R.id.edtNome);
        edtTelefone = findViewById(R.id.edtTelefone);
        edtEmail = findViewById(R.id.edtEmail);
        edtSenha = findViewById(R.id.edtSenha);
        edtVeiculo = findViewById(R.id.edtVeiculo);
        edtPlaca = findViewById(R.id.edtPlaca);
        edtCor = findViewById(R.id.edtCor);
        btnCadastrar = findViewById(R.id.btnCadastrar);
        btnVoltar = findViewById(R.id.btnVoltar);

        Intent intent = getIntent();
        if (intent.getBooleanExtra("motorista", false)) {
            isEditando = true;
            edtNome.setText(intent.getStringExtra("motorista_nome"));
            edtTelefone.setText(intent.getStringExtra("motorista_telefone"));
            edtEmail.setText(intent.getStringExtra("motorista_email"));
            edtVeiculo.setText(intent.getStringExtra("motorista_veiculo"));
            edtPlaca.setText(intent.getStringExtra("motorista_placa"));
            edtCor.setText(intent.getStringExtra("motorista_cor"));
            btnCadastrar.setText("ATUALIZAR CADASTRO");
        } else {
            btnCadastrar.setText("CADASTRAR");
        }

        btnVoltar.setOnClickListener(v -> finish());

        btnCadastrar.setOnClickListener(v -> realizarCadastro());
    }

    private void realizarCadastro() {
        String nome = edtNome.getText().toString().trim();
        String telefone = edtTelefone.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String senha = edtSenha.getText().toString().trim();
        String veiculo = edtVeiculo.getText().toString().trim();
        String placa = edtPlaca.getText().toString().trim().toUpperCase();
        String cor = edtCor.getText().toString().trim();

        // Validações...
        if (TextUtils.isEmpty(nome)) {
            edtNome.setError("Nome obrigatório");
            edtNome.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(telefone)) {
            edtTelefone.setError("Telefone obrigatório");
            edtTelefone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("E-mail obrigatório");
            edtEmail.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("E-mail inválido");
            edtEmail.requestFocus();
            return;
        }
        if (!isEditando && TextUtils.isEmpty(senha)) {
            edtSenha.setError("Senha obrigatória para novo cadastro");
            edtSenha.requestFocus();
            return;
        }
        if (!isEditando && senha.length() < 6) {
            edtSenha.setError("Senha deve ter pelo menos 6 caracteres");
            edtSenha.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(veiculo)) {
            edtVeiculo.setError("Modelo do veículo obrigatório");
            edtVeiculo.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(placa)) {
            edtPlaca.setError("Placa obrigatória");
            edtPlaca.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(cor)) {
            edtCor.setError("Cor do veículo obrigatória");
            edtCor.requestFocus();
            return;
        }

        // Caso seja edição, o usuário já está logado
        if (isEditando) {
            atualizarDadosNoFirestore();
            return;
        }

        // NOVO CADASTRO: criar usuário no Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Salva os dados no Firestore com o UID do usuário
                            salvarDadosMotorista(user.getUid(), nome, telefone, email, veiculo, placa, cor);
                        }
                    } else {
                        Toast.makeText(this, "Erro ao criar conta: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void salvarDadosMotorista(String uid, String nome, String telefone, String email, String veiculo, String placa, String cor) {
        Map<String, Object> motorista = new HashMap<>();
        motorista.put("nome", nome);
        motorista.put("telefone", telefone);
        motorista.put("email", email);
        motorista.put("veiculo", veiculo);
        motorista.put("placa", placa);
        motorista.put("cor", cor);
        motorista.put("uid", uid);

        db.collection("motoristas").document(uid)
                .set(motorista)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Cadastro realizado!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao salvar dados: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void atualizarDadosNoFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Usuário não autenticado", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = user.getUid();
        String nome = edtNome.getText().toString().trim();
        String telefone = edtTelefone.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String veiculo = edtVeiculo.getText().toString().trim();
        String placa = edtPlaca.getText().toString().trim().toUpperCase();
        String cor = edtCor.getText().toString().trim();

        Map<String, Object> motorista = new HashMap<>();
        motorista.put("nome", nome);
        motorista.put("telefone", telefone);
        motorista.put("email", email);
        motorista.put("veiculo", veiculo);
        motorista.put("placa", placa);
        motorista.put("cor", cor);
        motorista.put("uid", uid);

        db.collection("motoristas").document(uid)
                .set(motorista)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Dados atualizados!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao atualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}