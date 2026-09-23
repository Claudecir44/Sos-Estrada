package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityCadastroMotoristaBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cadastro novo (cria a conta + motoristas/{uid}) ou edição do cadastro do
// motorista logado (aberta por intentEdicao, carrega os dados do Firestore).
@AndroidEntryPoint
class CadastroMotoristaActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var motoristaRepository: IMotoristaRepository

    private lateinit var binding: ActivityCadastroMotoristaBinding
    private var editando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editando = intent.getBooleanExtra(EXTRA_EDITANDO, false)
        binding.btnCadastrar.text = if (editando) "ATUALIZAR CADASTRO" else "CADASTRAR"
        if (editando) {
            // O e-mail é o login da conta — mudar só no cadastro deixaria os
            // dois diferentes. A senha também não muda por aqui.
            binding.edtEmail.isEnabled = false
            binding.edtSenha.isEnabled = false
            carregarCadastro()
        }

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnCadastrar.setOnClickListener { realizarCadastro() }
    }

    private fun carregarCadastro() {
        lifecycleScope.launch {
            motoristaRepository.buscarMeuCadastro()
                .onSuccess { motorista ->
                    if (motorista == null) return@onSuccess
                    binding.edtNome.setText(motorista.nome)
                    binding.edtTelefone.setText(motorista.telefone)
                    binding.edtEmail.setText(motorista.email)
                    binding.edtVeiculo.setText(motorista.veiculo)
                    binding.edtPlaca.setText(motorista.placa)
                    binding.edtCor.setText(motorista.cor)
                }
                .onFailure { e ->
                    Toast.makeText(this@CadastroMotoristaActivity, "Erro ao buscar dados: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun realizarCadastro() {
        val nome = binding.edtNome.textoLimpo()
        val telefone = binding.edtTelefone.textoLimpo()
        val email = binding.edtEmail.textoLimpo()
        val senha = binding.edtSenha.textoLimpo()
        val veiculo = binding.edtVeiculo.textoLimpo()
        val placa = binding.edtPlaca.textoLimpo().uppercase()
        val cor = binding.edtCor.textoLimpo()

        when {
            nome.isEmpty() -> return binding.edtNome.erro("Nome obrigatório")
            telefone.isEmpty() -> return binding.edtTelefone.erro("Telefone obrigatório")
            email.isEmpty() -> return binding.edtEmail.erro("E-mail obrigatório")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return binding.edtEmail.erro("E-mail inválido")
            !editando && SenhaUtil.validar(senha) != null -> return binding.edtSenha.erro(SenhaUtil.validar(senha)!!)
            veiculo.isEmpty() -> return binding.edtVeiculo.erro("Modelo do veículo obrigatório")
            placa.isEmpty() -> return binding.edtPlaca.erro("Placa obrigatória")
            cor.isEmpty() -> return binding.edtCor.erro("Cor do veículo obrigatória")
        }

        val motorista = Motorista(nome = nome, telefone = telefone, email = email, veiculo = veiculo, placa = placa, cor = cor)
        binding.btnCadastrar.isEnabled = false
        lifecycleScope.launch {
            // Edição: o motorista já está logado. Cadastro novo: cria a conta antes.
            if (!editando) {
                val conta = authRepository.criarConta(email, senha)
                if (conta.isFailure) {
                    binding.btnCadastrar.isEnabled = true
                    Toast.makeText(this@CadastroMotoristaActivity, "Erro ao criar conta: ${conta.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
            motoristaRepository.salvarMeuCadastro(motorista)
                .onSuccess {
                    if (editando) {
                        Toast.makeText(this@CadastroMotoristaActivity, "✅ Dados atualizados!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        concluirCadastroNovo(email)
                    }
                }
                .onFailure { e ->
                    binding.btnCadastrar.isEnabled = true
                    val prefixo = if (editando) "Erro ao atualizar" else "Erro ao salvar dados"
                    Toast.makeText(this@CadastroMotoristaActivity, "$prefixo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Conta nova só entra depois de validar o e-mail: manda a verificação,
    // encerra a sessão e volta pro login com o aviso.
    private suspend fun concluirCadastroNovo(email: String) {
        authRepository.enviarVerificacaoEmail()
        authRepository.sair()
        AlertDialog.Builder(this)
            .setTitle("✅ Cadastro realizado!")
            .setMessage("Enviamos um e-mail de verificação para $email.\n\nAbra o link do e-mail (confira também o spam) e depois entre com seu e-mail e senha.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    private fun EditText.textoLimpo() = text.toString().trim()

    private fun EditText.erro(mensagem: String) {
        error = mensagem
        requestFocus()
    }

    companion object {
        private const val EXTRA_EDITANDO = "motorista"

        fun intentEdicao(context: Context): Intent =
            Intent(context, CadastroMotoristaActivity::class.java).putExtra(EXTRA_EDITANDO, true)
    }
}
