package com.cjstudio.sosestrada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.RECUSADO
import com.cjstudio.sosestrada.databinding.FragmentAtendimentoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Solicitações recebidas pelo prestador: aceitar, recusar, abrir o chat e
// excluir as canceladas. Abre logo abaixo do cartão "Atender solicitações",
// dentro do próprio painel (PrestadorDashboardActivity).
@AndroidEntryPoint
class AtendimentoFragment : Fragment() {

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    private lateinit var binding: FragmentAtendimentoBinding
    private lateinit var adapter: SolicitacaoAdapter
    private var primeiraCargaFeita = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAtendimentoBinding.inflate(inflater, container, false)
        return binding.root
    }

    // O login já é conferido pelo painel que hospeda esta lista.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SolicitacaoAdapter(
            aoAceitar = { s -> s.id?.let { aceitar(it) } },
            aoRecusar = { s -> s.id?.let { atualizarStatus(it, RECUSADO) } },
            aoExcluir = { s -> s.id?.let { excluir(it) } },
            aoVerLocalizacao = { s -> s.id?.let { id -> viewLifecycleOwner.lifecycleScope.launch { solicitacaoRepository.marcarLocalizacaoComoVista(id) } } }
        )
        binding.rvAtendimento.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAtendimento.adapter = adapter

        carregarSolicitacoes()
    }

    // Volta do chat: atualiza os contadores de mensagens novas.
    override fun onResume() {
        super.onResume()
        if (primeiraCargaFeita) carregarSolicitacoes()
    }

    // O painel chama ao reabrir a lista (ela fica escondida, não é recriada).
    fun recarregar() {
        if (primeiraCargaFeita && view != null) carregarSolicitacoes()
    }

    private fun carregarSolicitacoes() {
        primeiraCargaFeita = true
        binding.progressBarAtendimento.visibility = View.VISIBLE
        binding.tvEmptyAtendimento.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            solicitacaoRepository.recebidasPeloPrestador()
                .onSuccess { lista ->
                    adapter.atualizarLista(lista)
                    binding.tvEmptyAtendimento.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                    // Viu a lista: as solicitações novas deixam de contar na
                    // bolinha do painel e no ícone do app.
                    if (lista.any { it.novaParaPrestador }) solicitacaoRepository.marcarNovasComoVistas()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao carregar: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.tvEmptyAtendimento.visibility = View.VISIBLE
                }
            binding.progressBarAtendimento.visibility = View.GONE
        }
    }

    private fun aceitar(solicitacaoId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            solicitacaoRepository.aceitar(solicitacaoId)
                .onSuccess {
                    Toast.makeText(requireContext(), "Solicitação aceita! O motorista foi avisado pelo chat.", Toast.LENGTH_SHORT).show()
                    carregarSolicitacoes()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun atualizarStatus(solicitacaoId: String, status: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            solicitacaoRepository.atualizarStatus(solicitacaoId, status)
                .onSuccess {
                    Toast.makeText(requireContext(), "Solicitação ${SolicitacaoAdapter.descricaoStatus(status)}!", Toast.LENGTH_SHORT).show()
                    carregarSolicitacoes()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun excluir(solicitacaoId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            solicitacaoRepository.excluir(solicitacaoId)
                .onSuccess {
                    Toast.makeText(requireContext(), "Solicitação excluída permanentemente.", Toast.LENGTH_SHORT).show()
                    carregarSolicitacoes()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
