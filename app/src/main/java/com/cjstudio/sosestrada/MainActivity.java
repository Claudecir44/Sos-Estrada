package com.cjstudio.sosestrada;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    private CardView cardMotorista, cardPrestador;
    private TextView tvAdminAccess;

    // Credenciais fixas para acesso administrativo
    private static final String ADMIN_USER = "Programador";
    private static final String ADMIN_PASSWORD = "SENHA_REMOVIDA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardMotorista = findViewById(R.id.cardMotorista);
        cardPrestador = findViewById(R.id.cardPrestador);
        tvAdminAccess = findViewById(R.id.tvAdminAccess);

        // Motorista → LoginMotoristaActivity
        cardMotorista.setOnClickListener(v -> {
            salvarPerfil("motorista");
            startActivity(new Intent(MainActivity.this, LoginMotoristaActivity.class));
        });

        // Prestador → LoginPrestadorActivity
        cardPrestador.setOnClickListener(v -> {
            salvarPerfil("prestador");
            startActivity(new Intent(MainActivity.this, LoginPrestadorActivity.class));
        });

        // Acesso Admin → Exibe diálogo de autenticação
        tvAdminAccess.setOnClickListener(v -> mostrarDialogoAutenticacao());
    }

    private void salvarPerfil(String tipo) {
        SharedPreferences preferences = getSharedPreferences("SOSEstradaPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("tipoPerfil", tipo);
        editor.apply();
    }

    /**
     * Exibe um diálogo solicitando nome de usuário e senha para acesso administrativo.
     * As credenciais válidas são: Usuário = "Programador" e Senha = "SENHA_REMOVIDA"
     */
    private void mostrarDialogoAutenticacao() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 Acesso Administrativo");

        // Infla um layout personalizado com dois campos de texto
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_login, null);
        final EditText edtUsuario = view.findViewById(R.id.edtUsuario);
        final EditText edtSenha = view.findViewById(R.id.edtSenha);

        builder.setView(view);

        builder.setPositiveButton("Entrar", (dialog, which) -> {
            String usuario = edtUsuario.getText().toString().trim();
            String senha = edtSenha.getText().toString().trim();

            if (TextUtils.isEmpty(usuario) || TextUtils.isEmpty(senha)) {
                Toast.makeText(MainActivity.this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Verifica as credenciais
            if (usuario.equals(ADMIN_USER) && senha.equals(ADMIN_PASSWORD)) {
                // Credenciais corretas → abre AdminActivity
                startActivity(new Intent(MainActivity.this, AdminActivity.class));
            } else {
                Toast.makeText(MainActivity.this, "❌ Usuário ou senha incorretos.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}