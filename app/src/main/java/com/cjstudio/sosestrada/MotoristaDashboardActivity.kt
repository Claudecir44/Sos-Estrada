package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityMotoristaDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MotoristaDashboardActivity : AppCompatActivity() {

    companion object {
        // Notificação "Prestador respondeu"/"Mensagem Sos Estrada": abre com
        // a lista de prestadores já aberta (SosFirebaseMessagingService).
        const val EXTRA_ABRIR_LISTA = "abrirLista"
        private const val TAG_LISTA = "listaSocorro"
    }

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var motoristaRepository: IMotoristaRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    @Inject
    lateinit var notificacaoRepository: INotificacaoRepository

    private lateinit var binding: ActivityMotoristaDashboardBinding

    // Resultado ignorado: sem permissão o app funciona igual, só sem push.
    private val permissaoNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        // A lista de prestadores abre logo abaixo, aqui mesmo (não em outra tela).
        binding.btnSocorro.setOnClickListener { alternarListaSocorro() }
        binding.btnConfiguracoesMotorista.setOnClickListener {
            startActivity(ConfiguracoesActivity.intent(this, IChatRepository.MOTORISTA))
        }
        binding.btnMeuPerfilMotorista.setOnClickListener { startActivity(CadastroMotoristaActivity.intentEdicao(this)) }
        binding.ivFotoMotoristaPainel.setOnClickListener { startActivity(CadastroMotoristaActivity.intentEdicao(this)) }
        binding.btnVoltar.setOnClickListener {
            AppIconBadgeUtil.atualizar(this, 0)
            lifecycleScope.launch {
                // Antes do signOut: este celular para de receber os avisos desta conta.
                notificacaoRepository.removerToken(authRepository.uidLogado())
                authRepository.sair()
                startActivity(Intent(this@MotoristaDashboardActivity, LoginMotoristaActivity::class.java))
                finish()
            }
        }

        pedirPermissaoNotificacao(permissaoNotificacao)
        notificacaoRepository.registrarToken(authRepository.uidLogado())
        // Bolinha em "Preciso de socorro" e número no ícone do app, em tempo
        // real enquanto o painel existir (continua valendo com o socorro ou o
        // chat abertos por cima).
        lifecycleScope.launch {
            solicitacaoRepository.escutarAlertasMotorista().collect { total ->
                BadgeUtil.mostrar(binding.badgeSocorro, total)
                AppIconBadgeUtil.atualizar(this@MotoristaDashboardActivity, total)
                // Lista aberta: mostra a novidade (resposta, mensagem) na hora.
                if (binding.containerListaSocorro.isShown) fragmentoSocorro()?.recarregar()
            }
        }

        // Veio de uma notificação: já abre a lista.
        if (intent.getBooleanExtra(EXTRA_ABRIR_LISTA, false)) alternarListaSocorro(abrir = true)
    }

    // App já aberto e chegou outra notificação: abre a lista também.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::binding.isInitialized && intent.getBooleanExtra(EXTRA_ABRIR_LISTA, false)) alternarListaSocorro(abrir = true)
    }

    private fun fragmentoSocorro() = supportFragmentManager.findFragmentByTag(TAG_LISTA) as? SocorroFragment

    // Abre/fecha a lista de prestadores embaixo do cartão. O fragmento é
    // criado uma vez e só escondido ao fechar (reabrir recarrega os dados).
    private fun alternarListaSocorro(abrir: Boolean = !binding.containerListaSocorro.isShown) {
        if (abrir) {
            binding.containerListaSocorro.visibility = android.view.View.VISIBLE
            val existente = fragmentoSocorro()
            if (existente == null) {
                supportFragmentManager.beginTransaction()
                    .replace(binding.containerListaSocorro.id, SocorroFragment(), TAG_LISTA)
                    .commit()
            } else {
                existente.recarregar()
            }
            binding.tvSetaSocorro.text = "⌄"
            binding.root.post { binding.root.smoothScrollTo(0, binding.btnSocorro.top) }
        } else {
            binding.containerListaSocorro.visibility = android.view.View.GONE
            binding.tvSetaSocorro.text = "›"
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
            if (motorista?.bloqueado == true || authRepository.contaBloqueada()) {
                // Bloqueado pelo admin com a sessão já aberta: volta pro login.
                Toast.makeText(this@MotoristaDashboardActivity, MENSAGEM_BLOQUEADO, Toast.LENGTH_LONG).show()
                notificacaoRepository.removerToken(authRepository.uidLogado())
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
