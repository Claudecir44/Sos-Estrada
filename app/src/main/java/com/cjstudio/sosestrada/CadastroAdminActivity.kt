package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityCadastroAdminBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cadastro de uma conta de admin (mesmo modelo do Caronas): dados da
// pessoa + senha de login + senha do administrador master, que autoriza a
// criação. Depois de cadastrar, a pessoa valida o e-mail e entra pelo login.
@AndroidEntryPoint
class CadastroAdminActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var binding: ActivityCadastroAdminBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TelefoneUtil.aplicarMascara(binding.edtTelefone)
        CpfUtil.aplicarMascara(binding.edtCpf)
        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnCadastrar.setOnClickListener { cadastrar() }
    }

    private fun cadastrar() {
        val nome = binding.edtNome.textoLimpo()
        val sobrenome = binding.edtSobrenome.textoLimpo()
        val email = binding.edtEmail.textoLimpo()
        val telefone = binding.edtTelefone.textoLimpo()
        val cpf = binding.edtCpf.textoLimpo()
        val senha = binding.edtSenha.text.toString()
        val confirmarSenha = binding.edtConfirmarSenha.text.toString()
        val senhaMaster = binding.edtSenhaMaster.text.toString()

        when {
            nome.isEmpty() -> return binding.edtNome.erro("Informe o nome")
            sobrenome.isEmpty() -> return binding.edtSobrenome.erro("Informe o sobrenome")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return binding.edtEmail.erro("E-mail inválido")
            telefone.isEmpty() -> return binding.edtTelefone.erro("Informe o telefone")
            !CpfUtil.valido(cpf) -> return binding.edtCpf.erro("CPF inválido")
            SenhaUtil.validar(senha) != null -> return binding.edtSenha.erro(SenhaUtil.validar(senha)!!)
            senha != confirmarSenha -> return binding.edtConfirmarSenha.erro("As senhas não coincidem")
            senhaMaster.isEmpty() -> return binding.edtSenhaMaster.erro("Informe a senha do administrador master")
        }

        val admin = Admin(nome = nome, sobrenome = sobrenome, email = email, telefone = telefone, cpf = cpf)
        binding.btnCadastrar.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            adminRepository.cadastrarAdmin(admin, senha, senhaMaster)
                .onSuccess {
                    binding.progressBar.visibility = View.GONE
                    AlertDialog.Builder(this@CadastroAdminActivity)
                        .setTitle("✅ Admin cadastrado!")
                        .setMessage("Enviamos um e-mail de verificação para $email.\n\nAbra o link do e-mail (confira também o spam) e depois entre com seu e-mail e senha.")
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_EMAIL, email))
                            finish()
                        }
                        .show()
                }
                .onFailure { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnCadastrar.isEnabled = true
                    if (e is SenhaMasterIncorretaException) {
                        binding.edtSenhaMaster.erro(e.message.orEmpty())
                    } else {
                        Toast.makeText(this@CadastroAdminActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun EditText.textoLimpo() = text.toString().trim()

    private fun EditText.erro(mensagem: String) {
        error = mensagem
        requestFocus()
    }

    companion object {
        // E-mail cadastrado, devolvido pra já vir preenchido no login.
        const val EXTRA_EMAIL = "email"

        fun intent(context: Context) = Intent(context, CadastroAdminActivity::class.java)
    }
}
