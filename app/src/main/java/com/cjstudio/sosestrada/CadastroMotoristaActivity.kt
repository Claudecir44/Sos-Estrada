package com.cjstudio.sosestrada

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
        if (editando) carregarCadastro()

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
            !editando && senha.isEmpty() -> return binding.edtSenha.erro("Senha obrigatória para novo cadastro")
            !editando && senha.length < 6 -> return binding.edtSenha.erro("Senha deve ter pelo menos 6 caracteres")
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
                    val mensagem = if (editando) "✅ Dados atualizados!" else "✅ Cadastro realizado!"
                    Toast.makeText(this@CadastroMotoristaActivity, mensagem, Toast.LENGTH_LONG).show()
                    finish()
                }
                .onFailure { e ->
                    binding.btnCadastrar.isEnabled = true
                    val prefixo = if (editando) "Erro ao atualizar" else "Erro ao salvar dados"
                    Toast.makeText(this@CadastroMotoristaActivity, "$prefixo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
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
