package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityMotoristaDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MotoristaDashboardActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var motoristaRepository: IMotoristaRepository

    private lateinit var binding: ActivityMotoristaDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authRepository.uidLogado() == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginMotoristaActivity::class.java))
            finish()
            return
        }

        binding = ActivityMotoristaDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSocorro.setOnClickListener {
            startActivity(Intent(this, SocorroActivity::class.java))
        }
        binding.btnCadastrar.setOnClickListener {
            startActivity(CadastroMotoristaActivity.intentEdicao(this))
        }
        binding.btnExcluir.setOnClickListener { confirmarExclusao() }
        binding.btnVoltar.setOnClickListener {
            authRepository.sair()
            startActivity(Intent(this, LoginMotoristaActivity::class.java))
            finish()
        }
    }

    // Volta da edição do cadastro: atualiza nome e veículo do cabeçalho.
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) carregarCabecalho()
    }

    private fun carregarCabecalho() {
        lifecycleScope.launch {
            val motorista = motoristaRepository.buscarMeuCadastro().getOrNull()
            val primeiroNome = motorista?.nome?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            binding.tvSaudacaoMotorista.text = if (primeiroNome != null) "Olá, $primeiroNome!" else "Olá!"
            binding.tvAvatarMotoristaPainel.text = if (primeiroNome != null) iniciais(motorista?.nome) else "🚗"
            val veiculo = listOfNotNull(motorista?.veiculo, motorista?.placa, motorista?.cor)
                .filter { it.isNotBlank() }.joinToString(" • ")
            binding.tvVeiculoPainel.text = if (veiculo.isNotEmpty()) "🚗 $veiculo" else "🚗 Cadastre seu veículo em Editar cadastro"
        }
    }

    private fun confirmarExclusao() {
        val inputSenha = EditText(this).apply {
            hint = "Digite sua senha atual"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Excluir Cadastro")
            .setMessage("Tem certeza que deseja excluir permanentemente seu cadastro e conta? Esta ação não pode ser desfeita.")
            .setView(inputSenha)
            .setPositiveButton("Excluir") { _, _ ->
                val senha = inputSenha.text.toString().trim()
                if (senha.isEmpty()) {
                    Toast.makeText(this, "Digite sua senha", Toast.LENGTH_SHORT).show()
                } else {
                    excluirCadastro(senha)
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // Senha confirmada -> apaga motoristas/{uid} -> apaga a conta do Auth.
    private fun excluirCadastro(senha: String) {
        lifecycleScope.launch {
            if (authRepository.reautenticar(senha).isFailure) {
                Toast.makeText(this@MotoristaDashboardActivity, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val dados = motoristaRepository.excluirMeuCadastro()
            if (dados.isFailure) {
                Toast.makeText(this@MotoristaDashboardActivity, "Erro ao excluir dados: ${dados.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            authRepository.excluirConta()
                .onSuccess {
                    Toast.makeText(this@MotoristaDashboardActivity, "Cadastro e conta excluídos com sucesso.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@MotoristaDashboardActivity, MainActivity::class.java))
                    finishAffinity()
                }
                .onFailure { e ->
                    Toast.makeText(this@MotoristaDashboardActivity, "Erro ao excluir conta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
