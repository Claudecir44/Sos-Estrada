package com.cjstudio.sosestrada

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityPrestadorDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrestadorDashboardActivity : AppCompatActivity() {

    companion object {
        // Notificação de solicitação/mensagem/localização: abre com as
        // solicitações já abertas (SosFirebaseMessagingService).
        const val EXTRA_ABRIR_LISTA = "abrirLista"
        private const val TAG_LISTA = "listaAtendimento"
    }

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var prestadorRepository: IPrestadorRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    @Inject
    lateinit var notificacaoRepository: INotificacaoRepository

    private lateinit var binding: ActivityPrestadorDashboardBinding

    // Resultado ignorado: sem permissão o app funciona igual, só sem push.
    private val permissaoNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        // Reabrir o app cai direto aqui (MainActivity), sem pedir login de novo.
        SessaoUtil.salvarPerfil(this, IChatRepository.PRESTADOR)
        // Voltar não desloga: fecha a lista, se aberta; senão só minimiza o app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.containerListaAtendimento.isShown) alternarListaAtendimento(abrir = false)
                else moveTaskToBack(true)
            }
        })

        // As solicitações abrem logo abaixo, aqui mesmo (não em outra tela).
        binding.btnAtender.setOnClickListener { alternarListaAtendimento() }
        // Assinatura, termos e regras ficam nas Configurações.
        binding.btnConfiguracoesPrestador.setOnClickListener {
            startActivity(ConfiguracoesActivity.intent(this, IChatRepository.PRESTADOR))
        }
        binding.btnMeuPerfilPrestador.setOnClickListener { startActivity(CadastroPrestadorActivity.intentEdicao(this)) }
        binding.ivFotoPrestadorPainel.setOnClickListener { startActivity(CadastroPrestadorActivity.intentEdicao(this)) }
        binding.btnVoltar.setOnClickListener {
            AppIconBadgeUtil.atualizar(this, 0)
            lifecycleScope.launch {
                // Antes do signOut: este celular para de receber os avisos desta conta.
                notificacaoRepository.removerToken(authRepository.uidLogado())
                SessaoUtil.limpar(this@PrestadorDashboardActivity)
                authRepository.sair()
                irParaLogin()
                finish()
            }
        }

        pedirPermissaoNotificacao(permissaoNotificacao)
        notificacaoRepository.registrarToken(authRepository.uidLogado())
        // Bolinha em "Atender solicitações" e número no ícone do app, em
        // tempo real enquanto o painel existir.
        lifecycleScope.launch {
            solicitacaoRepository.escutarAlertasPrestador().collect { total ->
                BadgeUtil.mostrar(binding.badgeAtender, total)
                AppIconBadgeUtil.atualizar(this@PrestadorDashboardActivity, total)
                // Lista aberta: mostra a novidade (pedido, mensagem, localização) na hora.
                if (binding.containerListaAtendimento.isShown) fragmentoAtendimento()?.recarregar()
            }
        }

        // Veio de uma notificação: já abre as solicitações.
        if (intent.getBooleanExtra(EXTRA_ABRIR_LISTA, false)) alternarListaAtendimento(abrir = true)
    }

    // App já aberto e chegou outra notificação: abre a lista também.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::binding.isInitialized && intent.getBooleanExtra(EXTRA_ABRIR_LISTA, false)) alternarListaAtendimento(abrir = true)
    }

    // Depois de sair: login do prestador com a tela inicial por baixo (o
    // "voltar" do login leva à escolha de perfil, não fecha o app).
    private fun irParaLogin() {
        startActivities(arrayOf(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            Intent(this, LoginPrestadorActivity::class.java)
        ))
    }

    private fun fragmentoAtendimento() = supportFragmentManager.findFragmentByTag(TAG_LISTA) as? AtendimentoFragment

    // Abre/fecha as solicitações embaixo do cartão. O fragmento é criado uma
    // vez e só escondido ao fechar (reabrir recarrega os dados).
    private fun alternarListaAtendimento(abrir: Boolean = !binding.containerListaAtendimento.isShown) {
        if (abrir) {
            binding.containerListaAtendimento.visibility = android.view.View.VISIBLE
            val existente = fragmentoAtendimento()
            if (existente == null) {
                supportFragmentManager.beginTransaction()
                    .replace(binding.containerListaAtendimento.id, AtendimentoFragment(), TAG_LISTA)
                    .commit()
            } else {
                existente.recarregar()
            }
            binding.tvSetaAtender.text = "⌄"
            binding.root.post { binding.root.smoothScrollTo(0, binding.btnAtender.top) }
        } else {
            binding.containerListaAtendimento.visibility = android.view.View.GONE
            binding.tvSetaAtender.text = "›"
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
                notificacaoRepository.removerToken(authRepository.uidLogado())
                SessaoUtil.limpar(this@PrestadorDashboardActivity)
                authRepository.sair()
                irParaLogin()
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
