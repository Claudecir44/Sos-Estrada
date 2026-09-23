package com.cjstudio.sosestrada

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.RECUSADO
import com.cjstudio.sosestrada.databinding.ActivityAtendimentoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Solicitações recebidas pelo prestador: aceitar, recusar, abrir o chat e
// excluir as canceladas.
@AndroidEntryPoint
class AtendimentoActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    private lateinit var binding: ActivityAtendimentoBinding
    private lateinit var adapter: SolicitacaoAdapter
    private var primeiraCargaFeita = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authRepository.uidLogado() == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityAtendimentoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SolicitacaoAdapter(
            aoAceitar = { s -> s.id?.let { aceitar(it) } },
            aoRecusar = { s -> s.id?.let { atualizarStatus(it, RECUSADO) } },
            aoExcluir = { s -> s.id?.let { excluir(it) } }
        )
        binding.rvAtendimento.layoutManager = LinearLayoutManager(this)
        binding.rvAtendimento.adapter = adapter

        carregarSolicitacoes()
    }

    // Volta do chat: atualiza os contadores de mensagens novas.
    override fun onResume() {
        super.onResume()
        if (primeiraCargaFeita) carregarSolicitacoes()
    }

    private fun carregarSolicitacoes() {
        primeiraCargaFeita = true
        binding.progressBarAtendimento.visibility = View.VISIBLE
        binding.tvEmptyAtendimento.visibility = View.GONE
        lifecycleScope.launch {
            solicitacaoRepository.recebidasPeloPrestador()
                .onSuccess { lista ->
                    adapter.atualizarLista(lista)
                    binding.tvEmptyAtendimento.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { e ->
                    Toast.makeText(this@AtendimentoActivity, "Erro ao carregar: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.tvEmptyAtendimento.visibility = View.VISIBLE
                }
            binding.progressBarAtendimento.visibility = View.GONE
        }
    }

    private fun aceitar(solicitacaoId: String) {
        lifecycleScope.launch {
            solicitacaoRepository.aceitar(solicitacaoId)
                .onSuccess {
                    Toast.makeText(this@AtendimentoActivity, "Solicitação aceita! O motorista foi avisado pelo chat.", Toast.LENGTH_SHORT).show()
                    carregarSolicitacoes()
                }
                .onFailure { e ->
                    Toast.makeText(this@AtendimentoActivity, "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun atualizarStatus(solicitacaoId: String, status: String) {
        lifecycleScope.launch {
            solicitacaoRepository.atualizarStatus(solicitacaoId, status)
                .onSuccess {
                    Toast.makeText(this@AtendimentoActivity, "Solicitação ${SolicitacaoAdapter.descricaoStatus(status)}!", Toast.LENGTH_SHORT).show()
                    carregarSolicitacoes()
                }
                .onFailure { e ->
                    Toast.makeText(this@AtendimentoActivity, "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun excluir(solicitacaoId: String) {
        lifecycleScope.launch {
            solicitacaoRepository.excluir(solicitacaoId)
                .onSuccess {
                    Toast.makeText(this@AtendimentoActivity, "Solicitação excluída permanentemente.", Toast.LENGTH_SHORT).show()
                    carregarSolicitacoes()
                }
                .onFailure { e ->
                    Toast.makeText(this@AtendimentoActivity, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
