package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
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
        binding.btnMeuPerfilMotorista.setOnClickListener { startActivity(CadastroMotoristaActivity.intentEdicao(this)) }
        binding.ivFotoMotoristaPainel.setOnClickListener { startActivity(CadastroMotoristaActivity.intentEdicao(this)) }
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

    // Foto + primeiro nome (embaixo da foto) e o veículo do cadastro.
    private fun carregarCabecalho() {
        lifecycleScope.launch {
            // Se o motorista confirmou um e-mail novo, acerta o cadastro.
            authRepository.emailLogado()?.let { motoristaRepository.sincronizarEmail(it) }
            val motorista = motoristaRepository.buscarMeuCadastro().getOrNull()
            if (motorista?.bloqueado == true) {
                // Bloqueado pelo admin com a sessão já aberta: volta pro login.
                Toast.makeText(this@MotoristaDashboardActivity, MENSAGEM_BLOQUEADO, Toast.LENGTH_LONG).show()
                authRepository.sair()
                startActivity(Intent(this@MotoristaDashboardActivity, LoginMotoristaActivity::class.java))
                finish()
                return@launch
            }
            FotoUtil.mostrar(binding.ivFotoMotoristaPainel, motorista?.foto)
            val primeiroNome = motorista?.nome?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            binding.tvNomeMotoristaPainel.text = primeiroNome ?: "Motorista"
            val veiculo = listOfNotNull(motorista?.veiculo, motorista?.placa, motorista?.cor)
                .filter { it.isNotBlank() }.joinToString(" • ")
            binding.tvVeiculoPainel.text = if (veiculo.isNotEmpty()) "🚗 $veiculo" else "🚗 Cadastre seu veículo em Meu Perfil"
        }
    }
}
