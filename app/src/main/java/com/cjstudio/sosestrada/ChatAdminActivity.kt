package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityChatAdminBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

// Conversa do Chat Admin (aberta pelo chip "💬 Chat Admin" do painel):
// mensagens cifradas, ✓/✓✓ de leitura e apagar mensagem (só pra mim / pra
// todos), iguais às do Caronas e do painel web.
@AndroidEntryPoint
class ChatAdminActivity : AppCompatActivity() {

    @Inject
    lateinit var chatAdminRepository: IChatAdminRepository

    private lateinit var binding: ActivityChatAdminBinding
    private lateinit var conversa: ConversaAdmin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("DEPRECATION")
        window.statusBarColor = getColor(R.color.admin_cinza_escuro)

        @Suppress("DEPRECATION")
        conversa = intent.getSerializableExtra(EXTRA_CONVERSA) as? ConversaAdmin ?: return finish()
        val meuId = chatAdminRepository.meuUid() ?: return finish()

        binding.tvNomeOutroChatAdmin.text = conversa.nomeOutroAdmin(meuId) ?: "Administrador"
        FotoUtil.mostrar(binding.ivFotoOutroChatAdmin, intent.getStringExtra(EXTRA_FOTO))
        binding.btnVoltarChatAdmin.setOnClickListener { finish() }
        binding.btnEnviarChatAdmin.setOnClickListener { enviar() }

        val adapter = MensagemChatAdminAdapter(meuId) { m -> perguntarApagar(m, meuId) }
        binding.rvMensagensChatAdmin.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMensagensChatAdmin.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatAdminRepository.escutarMensagens(conversa.id!!)
                    .catch { e -> avisar("Erro ao carregar mensagens: ${e.message}") }
                    .collect { lista ->
                        adapter.atualizar(lista)
                        binding.tvVazioChatAdmin.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                        if (lista.isNotEmpty()) binding.rvMensagensChatAdmin.scrollToPosition(lista.size - 1)
                        // Chegou mensagem com a conversa aberta: já fica lida.
                        if (lista.any { it.destinatarioId == meuId && !it.lida }) chatAdminRepository.marcarConversaComoLida(conversa)
                    }
            }
        }
        lifecycleScope.launch { chatAdminRepository.marcarConversaComoLida(conversa) }
    }

    private fun enviar() {
        val texto = binding.edtMensagemChatAdmin.text.toString().trim()
        if (texto.isEmpty()) return
        binding.edtMensagemChatAdmin.setText("")
        lifecycleScope.launch {
            chatAdminRepository.enviarMensagem(conversa, texto)
                .onFailure { e ->
                    binding.edtMensagemChatAdmin.setText(texto)
                    avisar("Erro ao enviar: ${e.message}")
                }
        }
    }

    // "Apagar para todos" só pra quem mandou.
    private fun perguntarApagar(m: MensagemChatAdmin, meuId: String) {
        val opcoes = if (m.remetenteId == meuId) arrayOf("Apagar só para mim", "Apagar para todos") else arrayOf("Apagar só para mim")
        AlertDialog.Builder(this)
            .setTitle("Apagar mensagem")
            .setItems(opcoes) { _, qual ->
                lifecycleScope.launch {
                    val r = if (qual == 1) chatAdminRepository.apagarMensagemParaTodos(m) else chatAdminRepository.apagarMensagemParaMim(m)
                    r.onFailure { e -> avisar("Erro ao apagar: ${e.message}") }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun avisar(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_LONG).show()

    companion object {
        private const val EXTRA_CONVERSA = "conversaAdmin"
        private const val EXTRA_FOTO = "fotoOutro"

        fun intent(context: Context, conversa: ConversaAdmin, fotoOutro: String?) =
            Intent(context, ChatAdminActivity::class.java)
                .putExtra(EXTRA_CONVERSA, conversa)
                .putExtra(EXTRA_FOTO, fotoOutro)
    }
}
