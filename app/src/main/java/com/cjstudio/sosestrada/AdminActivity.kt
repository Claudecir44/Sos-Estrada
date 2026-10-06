package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityAdminBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do app admin: login e listas de motoristas/prestadores,
// com atalhos pra todas as solicitações e conversas.
@AndroidEntryPoint
class AdminActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    @Inject
    lateinit var termosRepository: ITermosRepository

    @Inject
    lateinit var chatAdminRepository: IChatAdminRepository

    private lateinit var binding: ActivityAdminBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Barra de status no cinza do cabeçalho, em vez do roxo do tema.
        // (No Android 15+ a barra é transparente e isso é ignorado.)
        @Suppress("DEPRECATION")
        window.statusBarColor = getColor(R.color.admin_cinza)

        // Sessão de um login anterior: mantém o admin logado entre aberturas.
        lifecycleScope.launch {
            if (adminRepository.temSessaoDeAdmin()) inicializarPainel() else pedirLogin()
        }
    }

    // Volta do cadastro de admin: reabre o login já com o e-mail cadastrado.
    private val cadastroAdmin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        pedirLogin(resultado.data?.getStringExtra(CadastroAdminActivity.EXTRA_EMAIL).orEmpty())
    }

    // Login por e-mail e senha. O diálogo fica aberto até entrar (ou
    // cancelar, que fecha a tela — não há conteúdo sem autenticar).
    private fun pedirLogin(emailInicial: String = "") {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_login, null)
        val edtEmail = view.findViewById<EditText>(R.id.edtEmailAdmin)
        val edtSenha = view.findViewById<EditText>(R.id.edtSenhaAdmin)
        edtEmail.setText(emailInicial)

        val dialogo = AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle("🔐 Acesso Administrativo")
            .setView(view)
            .setPositiveButton("Entrar", null)
            .setNeutralButton("Criar conta", null)
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .create()

        view.findViewById<View>(R.id.btnEsqueciSenhaAdmin).setOnClickListener {
            val email = edtEmail.text.toString().trim()
            if (email.isEmpty()) {
                edtEmail.error = "Digite seu e-mail para redefinir a senha"
                return@setOnClickListener
            }
            lifecycleScope.launch {
                adminRepository.enviarRedefinicaoSenha(email)
                    .onSuccess { avisar("📧 Enviamos um link para redefinir sua senha para $email. Confira também o spam.") }
                    .onFailure { e -> avisar("❌ Não foi possível enviar: ${e.message}") }
            }
        }

        // Botões ligados depois do show(): assim um erro de validação não fecha o diálogo.
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                lerCampos(edtEmail, edtSenha)?.let { (email, senha) -> entrar(dialogo, email, senha) }
            }
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialogo.dismiss()
                cadastroAdmin.launch(CadastroAdminActivity.intent(this))
            }
        }
        dialogo.show()
    }

    private fun lerCampos(edtEmail: EditText, edtSenha: EditText): Pair<String, String>? {
        val email = edtEmail.text.toString().trim()
        val senha = edtSenha.text.toString()
        if (email.isEmpty()) {
            edtEmail.error = "E-mail obrigatório"
            return null
        }
        SenhaUtil.validar(senha)?.let {
            edtSenha.error = it
            return null
        }
        return email to senha
    }

    private fun entrar(dialogo: AlertDialog, email: String, senha: String) {
        lifecycleScope.launch {
            adminRepository.entrar(email, senha)
                .onSuccess {
                    dialogo.dismiss()
                    inicializarPainel()
                }
                .onFailure { e ->
                    if (e is EmailNaoVerificadoException) {
                        VerificarEmailDialogUtil.mostrarLogin(this@AdminActivity, email, e.message.orEmpty().removePrefix("Valide seu cadastro pelo e-mail para poder entrar. "))
                        return@onFailure
                    }
                    val mensagem = if (e is IllegalStateException) {
                        e.message
                    } else {
                        "❌ E-mail ou senha incorretos."
                    }
                    avisar(mensagem ?: "❌ Não foi possível entrar.")
                }
        }
    }

    private fun avisar(mensagem: String) {
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
    }

    private fun inicializarPainel() {
        binding.rvLista.layoutManager = LinearLayoutManager(this)
        binding.grupoListas.setOnCheckedStateChangeListener { _, _ -> mostrarListaEscolhida() }
        // Sair só encerra a sessão e volta pro login (antes fechava o app).
        binding.btnSairAdmin.setOnClickListener {
            adminRepository.sair()
            recreate()
        }
        binding.btnMeuPerfil.setOnClickListener { meuPerfil.launch(CadastroAdminActivity.intentPerfil(this)) }
        binding.ivFotoAdminCabecalho.setOnClickListener { meuPerfil.launch(CadastroAdminActivity.intentPerfil(this)) }
        // Primeiro quem está logado (admin ou colaborador e o que ele pode
        // ver), depois as listas permitidas.
        lifecycleScope.launch {
            carregarSaudacao()
            // Primeira entrada como admin/colaborador: aceite dos termos (por perfil).
            if (perfil != null) {
                TermosAceiteDialogUtil.exigir(this@AdminActivity, ITermosRepository.PERFIL_ADMIN, termosRepository) {
                    adminRepository.sair()
                    recreate()
                }
            }
            painelIniciado = true
            carregarTudo()
        }
        iniciarChatAdmin()
    }

    // ---- 💬 Chat Admin (admins e colaboradores) ----
    private val chatAdminAdapter: ChatAdminConversaAdapter = ChatAdminConversaAdapter(
        aoTocar = { c -> startActivity(ChatAdminActivity.intent(this, c, chatAdminAdapter.fotoDe(c))) },
        aoSegurar = { c -> confirmarExclusaoConversa(c) }
    )
    private var contatosChat: List<ContatoAdmin> = emptyList()
    private var erroChatAdmin: String? = null

    private fun iniciarChatAdmin() {
        chatAdminRepository.registrarTokenAdmin()
        binding.btnNovaConversaAdmin.setOnClickListener { escolherContatoChat() }
        lifecycleScope.launch {
            chatAdminRepository.listarContatos().onSuccess { lista ->
                contatosChat = lista
                chatAdminAdapter.fotos = lista.associate { it.id to it.foto }
            }
        }
        // Lista e total de não lidas em tempo real, enquanto o painel está aberto.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatAdminRepository.escutarMinhasConversas()
                    .catch { e -> erroChatAdmin = e.message }
                    .collect { conversas ->
                        erroChatAdmin = null
                        val meuId = chatAdminRepository.meuUid()
                        chatAdminAdapter.atualizarLista(conversas, meuId)
                        val naoLidas = conversas.sumOf { it.naoLidasParaMim(meuId) }
                        binding.chipChatAdmin.text = if (naoLidas > 0) "💬 Chat Admin ($naoLidas)" else "💬 Chat Admin"
                        if (binding.grupoListas.checkedChipId == R.id.chipChatAdmin) mostrarListaEscolhida()
                    }
            }
        }
        // Toque na notificação do chat: já abre nesta seção.
        if (intent.getBooleanExtra(EXTRA_ABRIR_CHAT_ADMIN, false)) binding.chipChatAdmin.isChecked = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::binding.isInitialized && intent.getBooleanExtra(EXTRA_ABRIR_CHAT_ADMIN, false)) binding.chipChatAdmin.isChecked = true
    }

    private fun escolherContatoChat() {
        lifecycleScope.launch {
            if (contatosChat.isEmpty()) contatosChat = chatAdminRepository.listarContatos().getOrDefault(emptyList())
            if (contatosChat.isEmpty()) {
                avisar("Não há outro administrador ou colaborador cadastrado.")
                return@launch
            }
            val nomes = contatosChat.map { c ->
                (if (c.colaborador) "🤝 " else "🛡️ ") + c.nome + (c.email?.let { "  ·  $it" } ?: "")
            }
            AlertDialog.Builder(this@AdminActivity)
                .setTitle("Nova conversa")
                .setItems(nomes.toTypedArray()) { _, i ->
                    val contato = contatosChat[i]
                    lifecycleScope.launch {
                        chatAdminRepository.buscarOuCriarConversa(contato)
                            .onSuccess { c -> startActivity(ChatAdminActivity.intent(this@AdminActivity, c, contato.foto)) }
                            .onFailure { e -> avisar("Erro ao abrir a conversa: ${e.message}") }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun confirmarExclusaoConversa(c: ConversaAdmin) {
        val nome = c.nomeOutroAdmin(chatAdminRepository.meuUid()) ?: "Administrador"
        AlertDialog.Builder(this)
            .setTitle("Excluir conversa")
            .setMessage("Excluir toda a conversa com $nome? As mensagens somem para os dois lados, sem volta.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    chatAdminRepository.apagarConversa(c)
                        .onSuccess { avisar("Conversa excluída.") }
                        .onFailure { e -> avisar("Erro ao excluir: ${e.message}") }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Volta do Meu Perfil: perfil excluído -> sem acesso, volta pro login;
    // salvo -> atualiza foto e nome do cabeçalho.
    private val meuPerfil = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode == CadastroAdminActivity.RESULTADO_EXCLUIDO) recreate()
        else lifecycleScope.launch { carregarSaudacao() }
    }

    // Cadastro de quem está logado: admin completo ou colaborador (com as
    // seções que o admin master liberou). null = ainda não carregou.
    private var perfil: Admin? = null

    private fun pode(secao: String) = perfil?.pode(secao) ?: true

    // Foto + só o primeiro nome do cadastro de admin (embaixo da foto);
    // sem cadastro, cai no começo do e-mail. Colaborador: título próprio e
    // só as seções permitidas.
    private suspend fun carregarSaudacao() {
        val cadastro = adminRepository.buscarMeuCadastro().getOrNull()
        perfil = cadastro
        FotoUtil.mostrar(binding.ivFotoAdminCabecalho, cadastro?.foto)
        val nome = cadastro?.nome
        val primeiroNome = nome?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: adminRepository.emailLogado()?.substringBefore("@")
        binding.tvSaudacaoAdmin.text = primeiroNome ?: "Admin"
        binding.tvTituloPainelAdmin.text = if (cadastro?.ehColaborador == true) "Painel do Colaborador" else "Painel Administrativo"
        aplicarPermissoes()
    }

    private fun aplicarPermissoes() {
        val secoes = listOf(
            binding.chipMotoristas to Admin.PERM_MOTORISTAS,
            binding.chipPrestadores to Admin.PERM_PRESTADORES,
            binding.chipSolicitacoes to Admin.PERM_SOLICITACOES,
            binding.chipMensagens to Admin.PERM_MENSAGENS,
            binding.chipSugestoes to Admin.PERM_MENSAGENS,
            binding.chipFinanceiro to Admin.PERM_FINANCEIRO
        )
        secoes.forEach { (chip, secao) -> chip.visibility = if (pode(secao)) View.VISIBLE else View.GONE }
        binding.quadroMotoristas.alpha = if (pode(Admin.PERM_MOTORISTAS)) 1f else 0.4f
        binding.quadroPrestadores.alpha = if (pode(Admin.PERM_PRESTADORES)) 1f else 0.4f
        binding.quadroPedidos.alpha = if (pode(Admin.PERM_SOLICITACOES)) 1f else 0.4f
        // Seleção atual escondida: passa pra primeira seção liberada.
        val atual = secoes.firstOrNull { it.first.id == binding.grupoListas.checkedChipId }
        if (atual == null || !pode(atual.second)) secoes.firstOrNull { pode(it.second) }?.first?.isChecked = true
    }

    private val financeiro by lazy { FinanceiroAdmin(this, binding.secaoFinanceiro, adminRepository, lifecycleScope) }

    // Segurar um cartão: editar/bloquear/excluir só com a permissão "acoes".
    private fun abrirAcoes(abrir: () -> Unit) {
        if (pode(Admin.PERM_ACOES)) abrir()
        else avisar("🔒 Seu acesso de colaborador não permite editar, bloquear ou excluir cadastros.")
    }

    // Volta do chat: atualiza os totais e as listas.
    override fun onResume() {
        super.onResume()
        if (painelIniciado) carregarTudo()
    }

    // As quatro listas de uma vez (em paralelo) — os totais do cabeçalho
    // saem delas. Conversas e solicitações são a mesma coleção, mostradas
    // de dois jeitos.
    private fun carregarTudo() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            // Colaborador: só carrega o que pode ver (as regras negariam o resto).
            val semAcesso = Result.failure<Nothing>(IllegalStateException("🔒 Sem permissão"))
            val (motoristas, prestadores, solicitacoes) = coroutineScope {
                val m = async { if (pode(Admin.PERM_MOTORISTAS)) adminRepository.listarMotoristas() else semAcesso }
                val p = async { if (pode(Admin.PERM_PRESTADORES)) adminRepository.listarPrestadores() else semAcesso }
                val s = async {
                    if (pode(Admin.PERM_SOLICITACOES) || pode(Admin.PERM_MENSAGENS)) adminRepository.listarSolicitacoes() else semAcesso
                }
                Triple(m.await(), p.await(), s.await())
            }
            val manifestacoes = if (pode(Admin.PERM_MENSAGENS)) adminRepository.listarManifestacoes() else semAcesso
            binding.progressBar.visibility = View.GONE

            manifestacoes.onSuccess { lista ->
                manifestacaoAdapter.atualizarLista(lista)
                val novas = lista.count { it.status == Manifestacao.STATUS_NOVA }
                binding.chipSugestoes.text = if (novas > 0) "📝 Reclamações ($novas)" else "📝 Reclamações"
            }
            motoristas.onSuccess { motoristaAdapter.atualizarLista(it) }
            prestadores.onSuccess { prestadorAdapter.atualizarLista(it) }
            solicitacoes.onSuccess {
                solicitacaoAdapter.atualizarLista(it)
                conversaAdapter.atualizarLista(it)
            }
            binding.tvTotalMotoristas.text = motoristas.getOrNull()?.size?.toString() ?: "—"
            binding.tvTotalPrestadores.text = prestadores.getOrNull()?.size?.toString() ?: "—"
            binding.tvTotalSolicitacoes.text = if (pode(Admin.PERM_SOLICITACOES)) solicitacoes.getOrNull()?.size?.toString() ?: "—" else "—"

            erros = mapOf(
                R.id.chipMotoristas to motoristas.exceptionOrNull()?.message,
                R.id.chipPrestadores to prestadores.exceptionOrNull()?.message,
                R.id.chipSolicitacoes to solicitacoes.exceptionOrNull()?.message,
                R.id.chipMensagens to solicitacoes.exceptionOrNull()?.message,
                R.id.chipSugestoes to manifestacoes.exceptionOrNull()?.message,
                R.id.chipChatAdmin to erroChatAdmin
            )
            // Seção bloqueada pro colaborador não é erro: não avisa.
            val erroSecaoPermitida = listOf(
                Admin.PERM_MOTORISTAS to motoristas, Admin.PERM_PRESTADORES to prestadores, Admin.PERM_SOLICITACOES to solicitacoes
            ).firstOrNull { (secao, r) -> pode(secao) && r.isFailure }?.second?.exceptionOrNull()?.message
            erroSecaoPermitida?.let { avisar("Erro ao carregar: $it") }
            mostrarListaEscolhida()
        }
    }

    private var painelIniciado = false
    private var financeiroAberto = false
    private var erros: Map<Int, String?> = emptyMap()
    // Segurar o cartão: editar, bloquear ou excluir (com a senha master).
    private val acoes by lazy { AcoesCadastroAdmin(this, adminRepository) { carregarTudo() } }
    private val motoristaAdapter = MotoristaAdminAdapter { m -> abrirAcoes { acoes.abrir(m) } }
    private val prestadorAdapter = PrestadorAdminAdapter { p -> abrirAcoes { acoes.abrir(p) } }
    private val solicitacaoAdapter = AdminSolicitacaoAdapter()
    private val conversaAdapter = AdminConversaAdapter()
    private val manifestacaoAdapter = ManifestacaoAdminAdapter { m -> abrirManifestacao(m) }

    // Responder (vai por e-mail) ou arquivar uma reclamação/sugestão/denúncia.
    private fun abrirManifestacao(m: Manifestacao) {
        val id = m.id ?: return
        val detalhes = buildString {
            append(listOfNotNull(m.nome, m.email).joinToString(" · "))
            if (m.tipo == Manifestacao.DENUNCIA) append("\n\nDenunciado: ${m.denunciadoNome.orEmpty()}\nMotivo: ${m.motivo.orEmpty()}")
            append("\n\n${m.mensagem?.takeIf { it.isNotBlank() } ?: "(sem texto)"}")
            m.resposta?.let { append("\n\nResposta enviada:\n$it") }
        }
        val campo = android.widget.EditText(this).apply { hint = "Escreva a resposta (vai por e-mail)"; minLines = 3 }
        val arquivada = m.status == Manifestacao.STATUS_ARQUIVADA
        AlertDialog.Builder(this)
            .setTitle(Manifestacao.rotuloTipo(m.tipo))
            .setMessage(detalhes)
            .setView(campo)
            .setPositiveButton("Responder") { _, _ ->
                val resposta = campo.text.toString().trim()
                if (resposta.isEmpty()) { avisar("Escreva a resposta."); return@setPositiveButton }
                lifecycleScope.launch {
                    adminRepository.responderManifestacao(id, resposta)
                        .onSuccess { porEmail ->
                            avisar(if (porEmail) "Resposta enviada por e-mail." else "Resposta gravada, mas o e-mail do SOS Estrada ainda não está configurado no servidor — responda pelo e-mail ${m.email.orEmpty()}.")
                            carregarTudo()
                        }
                        .onFailure { e -> avisar("Não foi possível responder: ${e.message}") }
                }
            }
            .setNeutralButton(if (arquivada) "Desarquivar" else "Arquivar") { _, _ ->
                lifecycleScope.launch {
                    adminRepository.arquivarManifestacao(id, !arquivada)
                        .onSuccess { carregarTudo() }
                        .onFailure { e -> avisar("Não foi possível alterar: ${e.message}") }
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    // Troca a lista abaixo do seletor (sem abrir outra tela). O Financeiro
    // ocupa o lugar da lista, na mesma tela.
    private fun mostrarListaEscolhida() {
        val escolhido = binding.grupoListas.checkedChipId
        val noFinanceiro = escolhido == R.id.chipFinanceiro
        binding.btnNovaConversaAdmin.visibility = if (escolhido == R.id.chipChatAdmin) View.VISIBLE else View.GONE
        binding.secaoFinanceiro.root.visibility = if (noFinanceiro) View.VISIBLE else View.GONE
        binding.rvLista.visibility = if (noFinanceiro) View.GONE else View.VISIBLE
        // Recarrega os pagamentos cada vez que o chip é escolhido.
        if (!noFinanceiro) financeiroAberto = false
        if (noFinanceiro) {
            binding.tvListaVazia.visibility = View.GONE
            if (!financeiroAberto) {
                financeiroAberto = true
                financeiro.abrir()
            }
            return
        }
        val (adapter, vazio) = when (escolhido) {
            R.id.chipPrestadores -> prestadorAdapter to "Nenhum prestador cadastrado ainda."
            R.id.chipSolicitacoes -> solicitacaoAdapter to "Nenhuma solicitação encontrada."
            R.id.chipMensagens -> conversaAdapter to "Nenhuma conversa encontrada."
            R.id.chipSugestoes -> manifestacaoAdapter to "Nenhuma reclamação, sugestão ou denúncia."
            R.id.chipChatAdmin -> chatAdminAdapter to "Nenhuma conversa ainda.\nToque em \"+ Nova conversa\" para falar com outro administrador ou colaborador."
            else -> motoristaAdapter to "Nenhum motorista cadastrado ainda."
        }
        if (binding.rvLista.adapter !== adapter) binding.rvLista.adapter = adapter
        if (binding.progressBar.visibility == View.VISIBLE) return

        val erro = erros[escolhido]
        binding.tvListaVazia.text = if (erro != null) "Erro ao carregar.\n$erro" else vazio
        binding.tvListaVazia.visibility = if (erro != null || adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    companion object {
        // Notificação do Chat Admin -> abre o painel já no chip do chat.
        const val EXTRA_ABRIR_CHAT_ADMIN = "abrirChatAdmin"

        fun intentChatAdmin(context: Context) =
            Intent(context, AdminActivity::class.java).putExtra(EXTRA_ABRIR_CHAT_ADMIN, true)
    }
}
