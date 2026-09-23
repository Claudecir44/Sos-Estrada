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
        if (adminRepository.temSessao()) inicializarPainel() else pedirLogin()
    }

    // Cancelar ou errar a senha fecha a tela (não há conteúdo sem autenticar).
    private fun pedirLogin() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_admin_login, null)
        val edtUsuario = view.findViewById<EditText>(R.id.edtUsuario)
        val edtSenha = view.findViewById<EditText>(R.id.edtSenha)
        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle("🔐 Acesso Administrativo")
            .setView(view)
            .setPositiveButton("Entrar") { _, _ ->
                val usuario = edtUsuario.text.toString().trim()
                val senha = edtSenha.text.toString().trim()
                if (usuario.isEmpty() || senha.isEmpty()) {
                    Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    entrar(usuario, senha)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
            .show()
    }

    private fun entrar(usuario: String, senha: String) {
        lifecycleScope.launch {
            adminRepository.entrar(usuario, senha)
                .onSuccess { inicializarPainel() }
                .onFailure { e ->
                    if (e is IllegalArgumentException) {
                        Toast.makeText(this@AdminActivity, "❌ Usuário ou senha incorretos.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        // Falha do login anônimo: abre mesmo assim (como antes), mas
                        // as leituras vão falhar até habilitar o provedor no Console.
                        Toast.makeText(
                            this@AdminActivity,
                            "Erro ao autenticar admin: ${e.message}\nHabilite o login Anônimo no Firebase Console (Authentication > Sign-in method).",
                            Toast.LENGTH_LONG
                        ).show()
                        inicializarPainel()
                    }
                }
        }
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
