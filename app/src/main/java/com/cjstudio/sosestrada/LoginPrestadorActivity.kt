package com.cjstudio.sosestrada

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
                    Toast.makeText(this@LoginPrestadorActivity, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
                    binding.edtEmailLogin.setText("")
                    binding.edtSenhaLogin.setText("")
                    startActivity(Intent(this@LoginPrestadorActivity, PrestadorDashboardActivity::class.java))
                    finish()
                }
                .onFailure { e ->
                    binding.btnEntrar.isEnabled = true
                    if (e is EmailNaoVerificadoException) {
                        mostrarMensagem("📧 ${e.message}", erro = false)
                    } else {
                        mostrarMensagem("❌ Falha no login: ${e.message ?: "Erro desconhecido"}", erro = true)
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
                    mostrarMensagem("📧 Enviamos um link para redefinir sua senha para $email. Confira também o spam.", erro = false)
                }
                .onFailure { e ->
                    mostrarMensagem("❌ Não foi possível enviar: ${e.message}", erro = true)
                }
        }
    }

    // Caixa de mensagem do cartão: vermelha pra erro, verde pra aviso.
    private fun mostrarMensagem(texto: String, erro: Boolean) {
        val (fundo, cor) = if (erro) R.color.admin_vermelho_claro to 0xFFB91C1C.toInt() else R.color.prestador_verde_claro to 0xFF1B5E20.toInt()
        binding.tvMensagem.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, fundo))
        binding.tvMensagem.setTextColor(cor)
        binding.tvMensagem.text = texto
        binding.tvMensagem.visibility = View.VISIBLE
    }
}
