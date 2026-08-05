package com.cjstudio.sosestrada;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    private CardView cardMotorista, cardPrestador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardMotorista = findViewById(R.id.cardMotorista);
        cardPrestador = findViewById(R.id.cardPrestador);

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
    }

    private void salvarPerfil(String tipo) {
        SharedPreferences preferences = getSharedPreferences("SOSEstradaPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("tipoPerfil", tipo);
        editor.apply();
    }
}