package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do app de usuários: escolhe entre motorista e prestador. No
// canto superior direito, o Suporte (redefinir senha / reenviar o e-mail de
// validação do cadastro), no mesmo modelo do Caronas.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

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
        binding.tvSuporte.setOnClickListener { mostrarSuporte() }
    }

    private fun mostrarSuporte() {
        AlertDialog.Builder(this)
            .setTitle("Suporte")
            .setItems(arrayOf("🔑 Redefinir senha", "📧 Reenviar e-mail de validação")) { _, qual ->
                abrirFormulario(reenviarValidacao = qual == 1)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirFormulario(reenviarValidacao: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_suporte, null)
        val etEmail = view.findViewById<EditText>(R.id.etEmailSuporte)
        val etSenha = view.findViewById<EditText>(R.id.etSenhaSuporte)
        view.findViewById<TextView>(R.id.tvExplicacaoSuporte).text = if (reenviarValidacao) {
            "Não recebeu o e-mail para validar o cadastro? Informe o e-mail e a senha que você cadastrou e enviaremos um novo link. Confira também a caixa de spam."
        } else {
            "Informe o e-mail do seu cadastro para receber um link de redefinição de senha. Confira também a caixa de spam."
        }
        etSenha.visibility = if (reenviarValidacao) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (reenviarValidacao) "Reenviar e-mail de validação" else "Redefinir senha")
            .setView(view)
            .setPositiveButton("Enviar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        // Botão "Enviar" só fecha o diálogo se os campos estiverem certos.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = etEmail.text.toString().trim().lowercase()
                val senha = etSenha.text.toString()
                when {
                    !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                        Toast.makeText(this, "Informe um e-mail válido.", Toast.LENGTH_SHORT).show()
                    reenviarValidacao && senha.isEmpty() ->
                        Toast.makeText(this, "Informe a senha do cadastro.", Toast.LENGTH_SHORT).show()
                    else -> {
                        dialog.dismiss()
                        if (reenviarValidacao) reenviarValidacao(email, senha) else redefinirSenha(email)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun redefinirSenha(email: String) {
        lifecycleScope.launch {
            authRepository.enviarRedefinicaoSenha(email)
                .onSuccess {
                    avisar("Se houver um cadastro com esse e-mail, enviamos o link para criar uma nova senha. Confira também a caixa de spam.")
                }
                .onFailure { e -> avisar("Não foi possível enviar agora: ${e.message}") }
        }
    }

    private fun reenviarValidacao(email: String, senha: String) {
        lifecycleScope.launch {
            authRepository.reenviarValidacao(email, senha)
                .onSuccess { mensagem -> avisar(mensagem) }
                .onFailure { e ->
                    avisar(
                        when (e) {
                            is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException ->
                                "E-mail ou senha incorretos. Se esqueceu a senha, use \"Redefinir senha\"."
                            else -> "Não foi possível reenviar agora: ${e.message}"
                        }
                    )
                }
        }
    }

    private fun avisar(mensagem: String) {
        AlertDialog.Builder(this)
            .setTitle("Suporte")
            .setMessage(mensagem)
            .setPositiveButton("OK", null)
            .show()
    }
}
