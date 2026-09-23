package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityLoginPrestadorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginPrestadorActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    private lateinit var binding: ActivityLoginPrestadorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginPrestadorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEntrar.setOnClickListener { fazerLogin() }
        binding.btnCriarConta.setOnClickListener {
            startActivity(Intent(this, CadastroPrestadorActivity::class.java))
        }
        binding.btnVoltarLogin.setOnClickListener { finish() }
    }

    private fun fazerLogin() {
        val email = binding.edtEmailLogin.text.toString().trim()
        val senha = binding.edtSenhaLogin.text.toString().trim()

        if (email.isEmpty()) {
            binding.edtEmailLogin.error = "E-mail obrigatório"
            binding.edtEmailLogin.requestFocus()
            return
        }
        if (senha.isEmpty()) {
            binding.edtSenhaLogin.error = "Senha obrigatória"
            binding.edtSenhaLogin.requestFocus()
            return
        }

        binding.btnEntrar.isEnabled = false
        lifecycleScope.launch {
            authRepository.entrar(email, senha)
                .onSuccess {
                    Toast.makeText(this@LoginPrestadorActivity, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
                    binding.edtEmailLogin.setText("")
                    binding.edtSenhaLogin.setText("")
                    startActivity(Intent(this@LoginPrestadorActivity, PrestadorDashboardActivity::class.java))
                    finish()
                }
                .onFailure { e ->
                    binding.btnEntrar.isEnabled = true
                    binding.tvMensagem.visibility = View.VISIBLE
                    binding.tvMensagem.text = "❌ Falha no login: ${e.message ?: "Erro desconhecido"}"
                }
        }
    }
}
