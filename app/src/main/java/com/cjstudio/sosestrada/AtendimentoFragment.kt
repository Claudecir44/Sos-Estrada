package com.cjstudio.sosestrada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

        binding.progressBarAtendimento.visibility = View.VISIBLE
        escutarSolicitacoes()
    }

    private var solicitacoes: List<Solicitacao> = emptyList()

    // Tempo real: pedido novo, cancelamento, mensagem ou localização do
    // motorista aparecem na hora, sem reabrir a lista nem voltar à tela.
    private fun escutarSolicitacoes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                solicitacaoRepository.escutarRecebidasPeloPrestador().collect { lista ->
                    solicitacoes = lista
                    binding.progressBarAtendimento.visibility = View.GONE
                    adapter.atualizarLista(lista)
                    binding.tvEmptyAtendimento.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                    marcarNovasVistasSeVisivel()
                }
            }
        }
    }

    // Lista aberta na tela: as solicitações novas já foram vistas (tiram a
    // bolinha do painel e o número do ícone do app).
    private fun marcarNovasVistasSeVisivel() {
        if (view?.isShown != true || solicitacoes.none { it.novaParaPrestador }) return
        viewLifecycleOwner.lifecycleScope.launch { solicitacaoRepository.marcarNovasComoVistas() }
    }

    // O painel chama ao reabrir a lista (ela fica escondida, não é recriada).
    fun recarregar() {
        if (view != null) marcarNovasVistasSeVisivel()
    }

    private fun aceitar(solicitacaoId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            solicitacaoRepository.aceitar(solicitacaoId)
                .onSuccess {
                    Toast.makeText(requireContext(), "Solicitação aceita! O motorista foi avisado pelo chat.", Toast.LENGTH_SHORT).show()
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
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
