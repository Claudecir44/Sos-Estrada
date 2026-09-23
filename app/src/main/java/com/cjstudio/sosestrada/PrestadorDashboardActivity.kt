package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityPrestadorDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrestadorDashboardActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var prestadorRepository: IPrestadorRepository

    private lateinit var binding: ActivityPrestadorDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authRepository.uidLogado() == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginPrestadorActivity::class.java))
            finish()
            return
        }

        binding = ActivityPrestadorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtender.setOnClickListener {
            startActivity(Intent(this, AtendimentoActivity::class.java))
        }
        binding.btnEditar.setOnClickListener {
            startActivity(CadastroPrestadorActivity.intentEdicao(this))
        }
        binding.btnAssinatura.setOnClickListener {
            startActivity(Intent(this, AssinaturaActivity::class.java))
        }
        binding.btnExcluir.setOnClickListener { confirmarExclusao() }
        binding.btnMeuPerfilPrestador.setOnClickListener { startActivity(CadastroPrestadorActivity.intentEdicao(this)) }
        binding.ivFotoPrestadorPainel.setOnClickListener { startActivity(CadastroPrestadorActivity.intentEdicao(this)) }
        binding.btnVoltar.setOnClickListener {
            authRepository.sair()
            startActivity(Intent(this, LoginPrestadorActivity::class.java))
            finish()
        }
    }

    // Volta da edição do cadastro: atualiza logo, nome e serviço do cabeçalho.
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) carregarCabecalho()
    }

    // Logo (foto) + primeiro nome embaixo dela, e o serviço oferecido.
    private fun carregarCabecalho() {
        lifecycleScope.launch {
            val prestador = prestadorRepository.buscarMeuCadastro().getOrNull()
            FotoUtil.mostrar(binding.ivFotoPrestadorPainel, prestador?.logo)
            val primeiroNome = prestador?.nome?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            binding.tvNomePrestadorPainel.text = primeiroNome ?: "Prestador"
            binding.tvServicoPainel.text = "🔧 " + (prestador?.servico?.takeIf { it.isNotBlank() } ?: "Cadastre seu serviço em Meu Perfil")
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

    // Senha confirmada -> apaga prestadores/{uid} -> apaga a conta do Auth.
    private fun excluirCadastro(senha: String) {
        lifecycleScope.launch {
            if (authRepository.reautenticar(senha).isFailure) {
                Toast.makeText(this@PrestadorDashboardActivity, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val dados = prestadorRepository.excluirMeuCadastro()
            if (dados.isFailure) {
                Toast.makeText(this@PrestadorDashboardActivity, "Erro ao excluir dados: ${dados.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            authRepository.excluirConta()
                .onSuccess {
                    Toast.makeText(this@PrestadorDashboardActivity, "Cadastro e conta excluídos com sucesso.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@PrestadorDashboardActivity, MainActivity::class.java))
                    finishAffinity()
                }
                .onFailure { e ->
                    Toast.makeText(this@PrestadorDashboardActivity, "Erro ao excluir conta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
