package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityLoginMotoristaBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginMotoristaActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    private lateinit var binding: ActivityLoginMotoristaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // A tela de login é sempre exibida (sem pular direto pro painel).
        binding.btnEntrar.setOnClickListener { fazerLogin() }
        binding.btnCriarConta.setOnClickListener {
            startActivity(Intent(this, CadastroMotoristaActivity::class.java))
        }
        binding.btnVoltarLogin.setOnClickListener { finish() }
        binding.btnEsqueciSenha.setOnClickListener { enviarRedefinicaoSenha() }
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
                    Toast.makeText(this@LoginMotoristaActivity, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginMotoristaActivity, MotoristaDashboardActivity::class.java))
                    finish()
                }
                .onFailure { e ->
                    binding.btnEntrar.isEnabled = true
                    binding.tvMensagem.visibility = View.VISIBLE
                    binding.tvMensagem.text = if (e is EmailNaoVerificadoException) {
                        "📧 ${e.message}"
                    } else {
                        "❌ Falha no login: ${e.message ?: "Erro desconhecido"}"
                    }
                }
        }
    }

    private fun enviarRedefinicaoSenha() {
        val email = binding.edtEmailLogin.text.toString().trim()
        if (email.isEmpty()) {
            binding.edtEmailLogin.error = "Digite seu e-mail para redefinir a senha"
            binding.edtEmailLogin.requestFocus()
            return
        }
        lifecycleScope.launch {
            authRepository.enviarRedefinicaoSenha(email)
                .onSuccess {
                    binding.tvMensagem.visibility = View.VISIBLE
                    binding.tvMensagem.text = "📧 Enviamos um link para redefinir sua senha para $email. Confira também o spam."
                }
                .onFailure { e ->
                    binding.tvMensagem.visibility = View.VISIBLE
                    binding.tvMensagem.text = "❌ Não foi possível enviar: ${e.message}"
                }
        }
    }
}
