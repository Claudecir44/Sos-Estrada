package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
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
        binding.btnAssinatura.setOnClickListener {
            startActivity(Intent(this, AssinaturaActivity::class.java))
        }
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
            if (prestador?.bloqueado == true || authRepository.contaBloqueada()) {
                // Bloqueado pelo admin com a sessão já aberta: volta pro login.
                Toast.makeText(this@PrestadorDashboardActivity, MENSAGEM_BLOQUEADO, Toast.LENGTH_LONG).show()
                authRepository.sair()
                startActivity(Intent(this@PrestadorDashboardActivity, LoginPrestadorActivity::class.java))
                finish()
                return@launch
            }
            FotoUtil.mostrar(binding.ivFotoPrestadorPainel, prestador?.logo)
            val primeiroNome = prestador?.nome?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            binding.tvNomePrestadorPainel.text = primeiroNome ?: "Prestador"
            binding.tvServicoPainel.text = "🔧 " + (prestador?.servico?.takeIf { it.isNotBlank() } ?: "Cadastre seu serviço em Meu Perfil")
        }
    }
}
