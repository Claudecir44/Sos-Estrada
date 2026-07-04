package com.cjstudio.sosestrada;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginPrestadorActivity extends AppCompatActivity {

    private TextInputEditText edtEmail, edtSenha;
    private MaterialButton btnEntrar, btnCriarConta;
    private TextView tvMensagem, btnVoltar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_prestador);

        mAuth = FirebaseAuth.getInstance();

        edtEmail = findViewById(R.id.edtEmailLogin);
        edtSenha = findViewById(R.id.edtSenhaLogin);
        btnEntrar = findViewById(R.id.btnEntrar);
        btnCriarConta = findViewById(R.id.btnCriarConta);
        tvMensagem = findViewById(R.id.tvMensagem);
        btnVoltar = findViewById(R.id.btnVoltarLogin);

        btnEntrar.setOnClickListener(v -> fazerLogin());

        btnCriarConta.setOnClickListener(v -> {
            startActivity(new Intent(LoginPrestadorActivity.this, CadastroPrestadorActivity.class));
        });

        btnVoltar.setOnClickListener(v -> finish());
    }

    private void fazerLogin() {
        String email = edtEmail.getText().toString().trim();
        String senha = edtSenha.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("E-mail obrigatório");
            edtEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(senha)) {
            edtSenha.setError("Senha obrigatória");
            edtSenha.requestFocus();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            SharedPreferences prefs = getSharedPreferences("SOSEstradaPrefs", MODE_PRIVATE);
                            prefs.edit().putString("tipoPerfil", "prestador").apply();

                            Toast.makeText(LoginPrestadorActivity.this,
                                    "✅ Login realizado!", Toast.LENGTH_SHORT).show();

                            // ✅ Limpa os campos antes de navegar (opcional, mas solicitado)
                            edtEmail.setText("");
                            edtSenha.setText("");

                            startActivity(new Intent(LoginPrestadorActivity.this, PrestadorDashboardActivity.class));
                            finish();
                        }
                    } else {
                        String erro = task.getException() != null ?
                                task.getException().getMessage() : "Erro desconhecido";
                        tvMensagem.setVisibility(View.VISIBLE);
                        tvMensagem.setText("❌ Falha no login: " + erro);
                    }
                });
    }
}