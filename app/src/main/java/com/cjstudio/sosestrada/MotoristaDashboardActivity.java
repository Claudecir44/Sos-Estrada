package com.cjstudio.sosestrada;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MotoristaDashboardActivity extends AppCompatActivity {

    private MaterialButton btnSocorro, btnCadastrar;
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
        btnVoltar = findViewById(R.id.btnVoltar);

        // ✅ CORRIGIDO: abre a tela de solicitar socorro
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

        btnVoltar.setOnClickListener(v -> {
            startActivity(new Intent(MotoristaDashboardActivity.this, LoginMotoristaActivity.class));
            finish();
        });
    }
}