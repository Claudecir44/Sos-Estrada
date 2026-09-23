package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
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
    private val motoristaAdapter = MotoristaAdminAdapter()
    private val prestadorAdapter = PrestadorAdminAdapter()

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
        binding.tvSaudacaoAdmin.text = "Olá, ${adminRepository.emailLogado().orEmpty()}"
        binding.rvMotoristas.layoutManager = LinearLayoutManager(this)
        binding.rvPrestadores.layoutManager = LinearLayoutManager(this)
        binding.rvMotoristas.adapter = motoristaAdapter
        binding.rvPrestadores.adapter = prestadorAdapter

        binding.toggleListas.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) mostrarLista(motoristas = id == R.id.btnMotoristas)
        }
        binding.cardTotalMotoristas.setOnClickListener { binding.toggleListas.check(R.id.btnMotoristas) }
        binding.cardTotalPrestadores.setOnClickListener { binding.toggleListas.check(R.id.btnPrestadores) }
        binding.cardTotalSolicitacoes.setOnClickListener {
            startActivity(Intent(this, AdminSolicitacoesActivity::class.java))
        }
        binding.btnVerSolicitacoes.setOnClickListener {
            startActivity(Intent(this, AdminSolicitacoesActivity::class.java))
        }
        binding.btnVerMensagensAdmin.setOnClickListener {
            startActivity(Intent(this, AdminMensagensActivity::class.java))
        }
        binding.btnSairAdmin.setOnClickListener {
            adminRepository.sair()
            finish()
        }

        painelIniciado = true
        carregarTudo()
    }

    // Volta das telas de solicitações/mensagens: atualiza os totais e as listas.
    override fun onResume() {
        super.onResume()
        if (painelIniciado) carregarTudo()
    }

    // Motoristas, prestadores e solicitações de uma vez (em paralelo) — os
    // totais do resumo saem das próprias listas.
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
            binding.tvMensagemInicial.visibility = View.GONE

            motoristas.onSuccess { motoristaAdapter.atualizarLista(it) }
            prestadores.onSuccess { prestadorAdapter.atualizarLista(it) }
            binding.tvTotalMotoristas.text = motoristas.getOrNull()?.size?.toString() ?: "—"
            binding.tvTotalPrestadores.text = prestadores.getOrNull()?.size?.toString() ?: "—"
            binding.tvTotalSolicitacoes.text = solicitacoes.getOrNull()?.size?.toString() ?: "—"

            erroMotoristas = motoristas.exceptionOrNull()?.message
            erroPrestadores = prestadores.exceptionOrNull()?.message
            (erroMotoristas ?: erroPrestadores)?.let { avisar("Erro ao carregar cadastros: $it") }

            mostrarLista(motoristas = binding.toggleListas.checkedButtonId != R.id.btnPrestadores)
        }
    }

    private var painelIniciado = false
    private var erroMotoristas: String? = null
    private var erroPrestadores: String? = null

    private fun mostrarLista(motoristas: Boolean) {
        if (binding.progressBar.visibility == View.VISIBLE) return
        binding.rvMotoristas.visibility = if (motoristas) View.VISIBLE else View.GONE
        binding.rvPrestadores.visibility = if (motoristas) View.GONE else View.VISIBLE

        val (vazio, erro, total) = if (motoristas) {
            Triple(binding.tvEmptyMotoristas, erroMotoristas, motoristaAdapter.itemCount)
        } else {
            Triple(binding.tvEmptyPrestadores, erroPrestadores, prestadorAdapter.itemCount)
        }
        binding.tvEmptyMotoristas.visibility = View.GONE
        binding.tvEmptyPrestadores.visibility = View.GONE
        if (erro != null) {
            vazio.text = "Erro ao carregar.\n$erro"
            vazio.visibility = View.VISIBLE
        } else if (total == 0) {
            vazio.text = if (motoristas) "Nenhum motorista cadastrado ainda." else "Nenhum prestador cadastrado ainda."
            vazio.visibility = View.VISIBLE
        }
    }
}
