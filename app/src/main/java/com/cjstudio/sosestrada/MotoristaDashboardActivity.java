package com.cjstudio.sosestrada;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MotoristaDashboardActivity extends AppCompatActivity {

    private MaterialButton btnSocorro, btnCadastrar, btnExcluir;
    private TextView btnVoltar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motorista_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginMotoristaActivity.class));
            finish();
            return;
        }

        btnSocorro = findViewById(R.id.btnSocorro);
        btnCadastrar = findViewById(R.id.btnCadastrar);
        btnExcluir = findViewById(R.id.btnExcluir);
        btnVoltar = findViewById(R.id.btnVoltar);

        btnSocorro.setOnClickListener(v -> {
            startActivity(new Intent(MotoristaDashboardActivity.this, SocorroActivity.class));
        });

        btnCadastrar.setOnClickListener(v -> {
            db.collection("motoristas").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        Intent intent = new Intent(MotoristaDashboardActivity.this, CadastroMotoristaActivity.class);
                        if (documentSnapshot.exists()) {
                            intent.putExtra("motorista", true);
                            intent.putExtra("motorista_nome", documentSnapshot.getString("nome"));
                            intent.putExtra("motorista_telefone", documentSnapshot.getString("telefone"));
                            intent.putExtra("motorista_email", documentSnapshot.getString("email"));
                            intent.putExtra("motorista_veiculo", documentSnapshot.getString("veiculo"));
                            intent.putExtra("motorista_placa", documentSnapshot.getString("placa"));
                            intent.putExtra("motorista_cor", documentSnapshot.getString("cor"));
                        }
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(MotoristaDashboardActivity.this, "Erro ao buscar dados: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MotoristaDashboardActivity.this, CadastroMotoristaActivity.class));
                    });
        });

        // NOVO: Excluir cadastro
        btnExcluir.setOnClickListener(v -> confirmarExclusao());

        btnVoltar.setOnClickListener(v -> {
            startActivity(new Intent(MotoristaDashboardActivity.this, LoginMotoristaActivity.class));
            finish();
        });
    }

    private void confirmarExclusao() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Excluir Cadastro");
        builder.setMessage("Tem certeza que deseja excluir permanentemente seu cadastro e conta? Esta ação não pode ser desfeita.");

        final EditText inputSenha = new EditText(this);
        inputSenha.setHint("Digite sua senha atual");
        inputSenha.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(inputSenha);

        builder.setPositiveButton("Excluir", (dialog, which) -> {
            String senha = inputSenha.getText().toString().trim();
            if (TextUtils.isEmpty(senha)) {
                Toast.makeText(this, "Digite sua senha", Toast.LENGTH_SHORT).show();
                return;
            }
            reautenticarEExcluir(senha);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void reautenticarEExcluir(String senha) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String email = user.getEmail();
        if (email == null) {
            Toast.makeText(this, "E-mail não encontrado", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        excluirDados(user.getUid());
                    } else {
                        Toast.makeText(this, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void excluirDados(String uid) {
        db.collection("motoristas").document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(this, "Cadastro e conta excluídos com sucesso.", Toast.LENGTH_LONG).show();
                                        startActivity(new Intent(MotoristaDashboardActivity.this, MainActivity.class));
                                        finishAffinity();
                                    } else {
                                        Toast.makeText(this, "Erro ao excluir conta: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Toast.makeText(this, "Usuário não encontrado.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao excluir dados: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}