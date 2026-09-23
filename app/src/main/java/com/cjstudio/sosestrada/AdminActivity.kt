package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityAdminBinding
import dagger.hilt.android.AndroidEntryPoint
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
        if (adminRepository.temSessaoDeAdmin()) inicializarPainel() else pedirLogin()
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
                lerCampos(edtEmail, edtSenha)?.let { (email, senha) -> criarConta(dialogo, email, senha) }
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

    private fun criarConta(dialogo: AlertDialog, email: String, senha: String) {
        lifecycleScope.launch {
            adminRepository.criarConta(email, senha)
                .onSuccess {
                    dialogo.dismiss()
                    AlertDialog.Builder(this@AdminActivity)
                        .setTitle("✅ Conta criada!")
                        .setMessage("Enviamos um e-mail de verificação para $email.\n\nAbra o link do e-mail (confira também o spam) e depois entre com seu e-mail e senha.")
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ -> pedirLogin(email) }
                        .show()
                }
                .onFailure { e -> avisar("❌ ${e.message}") }
        }
    }

    private fun avisar(mensagem: String) {
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
    }

    private fun inicializarPainel() {
        binding.rvMotoristas.layoutManager = LinearLayoutManager(this)
        binding.rvPrestadores.layoutManager = LinearLayoutManager(this)
        binding.rvMotoristas.adapter = motoristaAdapter
        binding.rvPrestadores.adapter = prestadorAdapter

        mostrarMensagemInicial()

        binding.btnMotoristas.setOnClickListener { carregarMotoristas() }
        binding.btnPrestadores.setOnClickListener { carregarPrestadores() }
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
    }

    private fun mostrarMensagemInicial() {
        binding.rvMotoristas.visibility = View.GONE
        binding.rvPrestadores.visibility = View.GONE
        binding.tvEmptyMotoristas.visibility = View.GONE
        binding.tvEmptyPrestadores.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvMensagemInicial.visibility = View.VISIBLE
        destacarAba(motoristas = true)
    }

    private fun destacarAba(motoristas: Boolean) {
        val ativo = getColorStateList(android.R.color.holo_blue_light)
        val inativo = getColorStateList(android.R.color.darker_gray)
        binding.btnMotoristas.backgroundTintList = if (motoristas) ativo else inativo
        binding.btnPrestadores.backgroundTintList = if (motoristas) inativo else ativo
    }

    private fun mostrarAba(motoristas: Boolean) {
        binding.tvMensagemInicial.visibility = View.GONE
        binding.rvMotoristas.visibility = if (motoristas) View.VISIBLE else View.GONE
        binding.rvPrestadores.visibility = if (motoristas) View.GONE else View.VISIBLE
        binding.tvEmptyMotoristas.visibility = View.GONE
        binding.tvEmptyPrestadores.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        destacarAba(motoristas)
    }

    private fun carregarMotoristas() {
        mostrarAba(motoristas = true)
        lifecycleScope.launch {
            adminRepository.listarMotoristas()
                .onSuccess { lista ->
                    motoristaAdapter.atualizarLista(lista)
                    binding.tvEmptyMotoristas.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { e ->
                    Toast.makeText(this@AdminActivity, "Erro ao carregar motoristas: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.tvEmptyMotoristas.text = "Erro ao carregar motoristas.\n${e.message}"
                    binding.tvEmptyMotoristas.visibility = View.VISIBLE
                }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun carregarPrestadores() {
        mostrarAba(motoristas = false)
        lifecycleScope.launch {
            adminRepository.listarPrestadores()
                .onSuccess { lista ->
                    prestadorAdapter.atualizarLista(lista)
                    binding.tvEmptyPrestadores.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { e ->
                    Toast.makeText(this@AdminActivity, "Erro ao carregar prestadores: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.tvEmptyPrestadores.text = "Erro ao carregar prestadores.\n${e.message}"
                    binding.tvEmptyPrestadores.visibility = View.VISIBLE
                }
            binding.progressBar.visibility = View.GONE
        }
    }
}
