package com.cjstudio.sosestrada

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Chat de uma solicitação entre motorista e prestador. Com EXTRA_READ_ONLY
// (painel admin) só mostra as mensagens: sem campo de envio e sem marcar
// nada como lido.
@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    @Inject
    lateinit var chatRepository: IChatRepository

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var solicitacaoId: String
    private var meuTipo: String? = null
    private var somenteLeitura = false

    private val escolherImagem = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) enviarImagem(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_SOLICITACAO_ID)
        if (id.isNullOrEmpty()) {
            Toast.makeText(this, "Serviço inválido para o chat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        solicitacaoId = id
        meuTipo = intent.getStringExtra(EXTRA_MEU_TIPO)
        somenteLeitura = intent.getBooleanExtra(EXTRA_READ_ONLY, false)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTituloChat.text = intent.getStringExtra(EXTRA_TITULO)?.takeIf { it.isNotEmpty() } ?: "Chat"
        binding.btnVoltarChat.setOnClickListener { finish() }

        adapter = ChatAdapter(if (somenteLeitura) null else meuTipo)
        binding.rvMensagens.layoutManager = LinearLayoutManager(this)
        binding.rvMensagens.adapter = adapter

        if (somenteLeitura) {
            binding.llInputMensagem.visibility = View.GONE
        } else {
            binding.btnAnexarFoto.setOnClickListener { escolherImagem.launch("image/*") }
            binding.btnEnviarMensagem.setOnClickListener { enviarTexto() }
            lifecycleScope.launch {
                chatRepository.zerarNaoLidas(solicitacaoId, meuTipo.orEmpty())
                chatRepository.limparMensagensAntigas(solicitacaoId)
            }
        }

        // Escuta só com a tela visível (mesmo ciclo do onStart/onStop de antes).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatRepository.escutarMensagens(solicitacaoId).collect { mensagens -> mostrar(mensagens) }
            }
        }
    }

    private suspend fun mostrar(mensagens: List<Mensagem>) {
        adapter.atualizarLista(mensagens)
        binding.tvEmptyChat.visibility = if (mensagens.isEmpty()) View.VISIBLE else View.GONE
        if (mensagens.isNotEmpty()) binding.rvMensagens.scrollToPosition(mensagens.size - 1)
        val tipo = meuTipo
        if (!somenteLeitura && tipo != null) chatRepository.marcarComoLidas(solicitacaoId, mensagens, tipo)
    }

    private fun enviarTexto() {
        val texto = binding.edtMensagem.text.toString().trim()
        if (texto.isEmpty()) return
        binding.edtMensagem.setText("")
        enviar(texto = texto, imagemUrl = null)
    }

    private fun enviarImagem(uri: Uri) {
        Toast.makeText(this, "Enviando foto...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            chatRepository.enviarImagem(solicitacaoId, uri)
                .onSuccess { url -> enviar(texto = null, imagemUrl = url) }
                .onFailure { e ->
                    Toast.makeText(this@ChatActivity, "Erro ao enviar foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun enviar(texto: String?, imagemUrl: String?) {
        val tipo = meuTipo ?: return
        lifecycleScope.launch {
            chatRepository.enviarMensagem(solicitacaoId, tipo, texto, imagemUrl)
                .onFailure { e ->
                    Toast.makeText(this@ChatActivity, "Erro ao enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    companion object {
        const val EXTRA_SOLICITACAO_ID = "solicitacao_id"
        const val EXTRA_MEU_TIPO = "meu_tipo" // "motorista" ou "prestador"
        const val EXTRA_TITULO = "titulo_chat"
        const val EXTRA_READ_ONLY = "read_only"
    }
}
