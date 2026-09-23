package com.cjstudio.sosestrada

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
                    // Conta bloqueada pelo admin não entra.
                    if (authRepository.contaBloqueada()) {
                        authRepository.sair()
                        binding.btnEntrar.isEnabled = true
                        mostrarMensagem(MENSAGEM_BLOQUEADO, erro = true)
                        return@launch
                    }
                    Toast.makeText(this@LoginMotoristaActivity, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginMotoristaActivity, MotoristaDashboardActivity::class.java))
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

    // Caixa de mensagem do cartão: vermelha pra erro, azul pra aviso.
    private fun mostrarMensagem(texto: String, erro: Boolean) {
        val (fundo, cor) = if (erro) R.color.admin_vermelho_claro to 0xFFB91C1C.toInt() else R.color.motorista_azul_claro to 0xFF0D47A1.toInt()
        binding.tvMensagem.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, fundo))
        binding.tvMensagem.setTextColor(cor)
        binding.tvMensagem.text = texto
        binding.tvMensagem.visibility = View.VISIBLE
    }
}
