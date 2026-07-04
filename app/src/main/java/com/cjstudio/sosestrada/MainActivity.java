package com.cjstudio.sosestrada;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    private CardView cardMotorista, cardPrestador;
    private TextView tvAdminAccess;

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

        // Acesso Admin → abre AdminActivity
        tvAdminAccess.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AdminActivity.class));
        });
    }

    private void salvarPerfil(String tipo) {
        SharedPreferences preferences = getSharedPreferences("SOSEstradaPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("tipoPerfil", tipo);
        editor.apply();
    }
}