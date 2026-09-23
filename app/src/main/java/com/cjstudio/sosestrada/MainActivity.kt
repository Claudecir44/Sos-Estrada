package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cjstudio.sosestrada.databinding.ActivityMainBinding

// Tela inicial do app de usuários: escolhe entre motorista e prestador.
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardMotorista.setOnClickListener {
            startActivity(Intent(this, LoginMotoristaActivity::class.java))
        }
        binding.cardPrestador.setOnClickListener {
            startActivity(Intent(this, LoginPrestadorActivity::class.java))
        }
    }
}
