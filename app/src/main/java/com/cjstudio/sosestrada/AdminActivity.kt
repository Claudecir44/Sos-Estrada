package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityAdminBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tela inicial do app admin: login e listas de motoristas/prestadores,
// com atalhos pra todas as solicitações e conversas.
@AndroidEntryPoint
class AdminActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var binding: ActivityAdminBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                    val mensagem = if (e is EmailNaoVerificadoException || e is IllegalStateException) {
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
        carregarSaudacao()
        binding.rvLista.layoutManager = LinearLayoutManager(this)
        binding.grupoListas.setOnCheckedStateChangeListener { _, _ -> mostrarListaEscolhida() }
        binding.btnSairAdmin.setOnClickListener {
            adminRepository.sair()
            finish()
        }
        binding.btnMeuPerfil.setOnClickListener { meuPerfil.launch(CadastroAdminActivity.intentPerfil(this)) }
        binding.ivFotoAdminCabecalho.setOnClickListener { meuPerfil.launch(CadastroAdminActivity.intentPerfil(this)) }
        painelIniciado = true
        carregarTudo()
    }

    // Volta do Meu Perfil: perfil excluído -> sem acesso, volta pro login;
    // salvo -> atualiza foto e nome do cabeçalho.
    private val meuPerfil = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode == CadastroAdminActivity.RESULTADO_EXCLUIDO) recreate() else carregarSaudacao()
    }

    // Foto + só o primeiro nome do cadastro de admin (embaixo da foto);
    // sem cadastro, cai no começo do e-mail.
    private fun carregarSaudacao() {
        lifecycleScope.launch {
            val cadastro = adminRepository.buscarMeuCadastro().getOrNull()
            FotoUtil.mostrar(binding.ivFotoAdminCabecalho, cadastro?.foto)
            val nome = cadastro?.nome
            val primeiroNome = nome?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
                ?: adminRepository.emailLogado()?.substringBefore("@")
            binding.tvSaudacaoAdmin.text = primeiroNome ?: "Admin"
        }
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
            val (motoristas, prestadores, solicitacoes) = coroutineScope {
                val m = async { adminRepository.listarMotoristas() }
                val p = async { adminRepository.listarPrestadores() }
                val s = async { adminRepository.listarSolicitacoes() }
                Triple(m.await(), p.await(), s.await())
            }
            binding.progressBar.visibility = View.GONE

            motoristas.onSuccess { motoristaAdapter.atualizarLista(it) }
            prestadores.onSuccess { prestadorAdapter.atualizarLista(it) }
            solicitacoes.onSuccess {
                solicitacaoAdapter.atualizarLista(it)
                conversaAdapter.atualizarLista(it)
            }
            binding.tvTotalMotoristas.text = motoristas.getOrNull()?.size?.toString() ?: "—"
            binding.tvTotalPrestadores.text = prestadores.getOrNull()?.size?.toString() ?: "—"
            binding.tvTotalSolicitacoes.text = solicitacoes.getOrNull()?.size?.toString() ?: "—"

            erros = mapOf(
                R.id.chipMotoristas to motoristas.exceptionOrNull()?.message,
                R.id.chipPrestadores to prestadores.exceptionOrNull()?.message,
                R.id.chipSolicitacoes to solicitacoes.exceptionOrNull()?.message,
                R.id.chipMensagens to solicitacoes.exceptionOrNull()?.message
            )
            erros.values.firstOrNull { it != null }?.let { avisar("Erro ao carregar: $it") }
            mostrarListaEscolhida()
        }
    }

    private var painelIniciado = false
    private var erros: Map<Int, String?> = emptyMap()
    private val motoristaAdapter = MotoristaAdminAdapter()
    private val prestadorAdapter = PrestadorAdminAdapter()
    private val solicitacaoAdapter = AdminSolicitacaoAdapter()
    private val conversaAdapter = AdminConversaAdapter()

    // Troca a lista abaixo do seletor (sem abrir outra tela).
    private fun mostrarListaEscolhida() {
        val escolhido = binding.grupoListas.checkedChipId
        val (adapter, vazio) = when (escolhido) {
            R.id.chipPrestadores -> prestadorAdapter to "Nenhum prestador cadastrado ainda."
            R.id.chipSolicitacoes -> solicitacaoAdapter to "Nenhuma solicitação encontrada."
            R.id.chipMensagens -> conversaAdapter to "Nenhuma conversa encontrada."
            else -> motoristaAdapter to "Nenhum motorista cadastrado ainda."
        }
        if (binding.rvLista.adapter !== adapter) binding.rvLista.adapter = adapter
        if (binding.progressBar.visibility == View.VISIBLE) return

        val erro = erros[escolhido]
        binding.tvListaVazia.text = if (erro != null) "Erro ao carregar.\n$erro" else vazio
        binding.tvListaVazia.visibility = if (erro != null || adapter.itemCount == 0) View.VISIBLE else View.GONE
    }
}
