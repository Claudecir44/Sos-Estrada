package com.cjstudio.sosestrada

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.CANCELADO
import com.cjstudio.sosestrada.databinding.FragmentSocorroBinding
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// Busca de socorro do motorista: lista os prestadores ativos (com distância,
// quando há GPS), pede o serviço, cancela e exclui solicitações. Abre logo
// abaixo do cartão "Preciso de socorro", dentro do próprio painel
// (MotoristaDashboardActivity), e não numa tela separada.
@AndroidEntryPoint
class SocorroFragment : Fragment() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var prestadorRepository: IPrestadorRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    @Inject
    lateinit var localizacaoRepository: ILocalizacaoRepository

    @Inject
    lateinit var avaliacaoRepository: IAvaliacaoRepository

    private lateinit var binding: FragmentSocorroBinding
    private lateinit var adapter: PrestadorAdapter

    private var latitude = 0.0
    private var longitude = 0.0
    private var temLocalizacao = false
    private var primeiraCargaFeita = false

    private val permissaoLocalizacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            obterLocalizacaoECarregar()
        } else {
            Toast.makeText(requireContext(), "Permissão de localização negada. A distância não será calculada.", Toast.LENGTH_LONG).show()
            carregarPrestadores()
        }
    }

    // Prestador escolhido no "Enviar Minha Localização" enquanto a permissão é pedida.
    private var enviarLocalizacaoPara: Prestador? = null

    private val permissaoParaEnviarLocalizacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        val prestador = enviarLocalizacaoPara
        enviarLocalizacaoPara = null
        if (concedida && prestador != null) {
            enviarMinhaLocalizacao(prestador)
        } else {
            Toast.makeText(requireContext(), "Sem permissão de localização não é possível enviar sua localização.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSocorroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PrestadorAdapter(
            aoSolicitar = ::solicitarServico,
            aoSegurar = ::aoSegurarPrestador,
            aoEnviarLocalizacao = ::aoEnviarLocalizacao,
            aoAvaliar = ::avaliarPrestador
        )
        binding.rvPrestadoresSocorro.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPrestadoresSocorro.adapter = adapter
        binding.edtPesquisa.doOnTextChanged { texto, _, _, _ -> adapter.filtrar(texto?.toString().orEmpty()) }
        escutarSolicitacoes()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obterLocalizacaoECarregar()
        } else {
            permissaoLocalizacao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Prestadores já carregados (com distância) e a solicitação mais recente
    // com cada um — esta vem em tempo real (escutarSolicitacoes), então
    // status, mensagens não lidas e botões mudam na hora, sem reabrir a lista.
    private var prestadores: List<Prestador> = emptyList()
    private var solicitacoes: Map<String, Solicitacao> = emptyMap()

    private fun escutarSolicitacoes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                solicitacaoRepository.escutarMinhasSolicitacoesPorPrestador().collect {
                    solicitacoes = it
                    aplicarSolicitacoes()
                }
            }
        }
    }

    // Junta o status de cada solicitação no cartão do prestador e redesenha.
    private fun aplicarSolicitacoes() {
        if (!primeiraCargaFeita) return
        for (p in prestadores) {
            val solicitacao = solicitacoes[p.uid]
            p.statusSolicitacao = solicitacao?.status
            p.solicitacaoId = solicitacao?.id
            p.naoLidasMotorista = solicitacao?.naoLidasMotorista ?: 0
            p.localizacaoEnviada = solicitacao?.latitudeCompartilhada != null
        }
        adapter.atualizarLista(prestadores)
        marcarRespostasVistasSeVisivel()
    }

    // Lista aberta na tela: as respostas do prestador já foram vistas (tiram
    // a bolinha do painel e o número do ícone do app).
    private fun marcarRespostasVistasSeVisivel() {
        if (view?.isShown != true || solicitacoes.values.none { it.respostaNaoVistaMotorista }) return
        viewLifecycleOwner.lifecycleScope.launch { solicitacaoRepository.marcarRespostasComoVistas() }
    }

    // O painel chama ao reabrir a lista (ela fica escondida, não é recriada):
    // busca de novo os prestadores (pode ter entrado um novo).
    fun recarregar() {
        if (primeiraCargaFeita && view != null) carregarPrestadores()
    }

    private fun obterLocalizacaoECarregar() {
        viewLifecycleOwner.lifecycleScope.launch {
            localizacaoRepository.ultimaLocalizacao()
                .onSuccess { local ->
                    if (local != null) {
                        latitude = local.latitude
                        longitude = local.longitude
                        temLocalizacao = true
                    } else {
                        Toast.makeText(requireContext(), "Não foi possível obter a localização. Verifique o GPS.", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao obter localização: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            carregarPrestadores()
        }
    }

    private fun carregarPrestadores() {
        primeiraCargaFeita = true
        binding.progressBarSocorro.visibility = View.VISIBLE
        binding.tvEmptySocorro.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val meuUid = authRepository.uidLogado()
            val prestadores = prestadorRepository.listarAtivos().getOrElse { e ->
                binding.progressBarSocorro.visibility = View.GONE
                binding.tvEmptySocorro.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Erro ao carregar prestadores: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
                // Quem também é prestador (mesma conta) não pede socorro a si mesmo.
                .filter { it.uid != meuUid }
            if (temLocalizacao) calcularDistancias(prestadores)

            this@SocorroFragment.prestadores = prestadores
            carregarAvaliacoes()
            binding.progressBarSocorro.visibility = View.GONE
            binding.tvEmptySocorro.visibility = if (prestadores.isEmpty()) View.VISIBLE else View.GONE
            // Status das solicitações: os últimos recebidos em tempo real.
            aplicarSolicitacoes()
        }
    }

    // Nota de cada prestador da lista + atendimentos que este motorista já
    // avaliou (escondem o botão Avaliar).
    private fun carregarAvaliacoes() {
        viewLifecycleOwner.lifecycleScope.launch {
            val notas = avaliacaoRepository.notasDe(prestadores.mapNotNull { it.uid }).getOrDefault(emptyMap())
            val jaAvaliadas = avaliacaoRepository.solicitacoesJaAvaliadas().getOrDefault(emptySet())
            adapter.atualizarAvaliacoes(notas, jaAvaliadas)
        }
    }

    // Só depois que o prestador aceitou (o botão só aparece assim; as regras
    // também conferem).
    private fun avaliarPrestador(prestador: Prestador) {
        val solicitacaoId = prestador.solicitacaoId ?: return
        val prestadorUid = prestador.uid ?: return
        AvaliacaoDialogUtil.mostrar(requireContext(), prestador.nome) { nota, comentario ->
            viewLifecycleOwner.lifecycleScope.launch {
                avaliacaoRepository.avaliar(solicitacaoId, prestadorUid, nota, comentario)
                    .onSuccess {
                        Toast.makeText(requireContext(), "Obrigado pela avaliação!", Toast.LENGTH_SHORT).show()
                        carregarAvaliacoes()
                    }
                    .onFailure { e ->
                        Toast.makeText(requireContext(), "Não foi possível avaliar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    // Geocodifica os endereços em paralelo (cada um é uma chamada de rede).
    private suspend fun calcularDistancias(prestadores: List<Prestador>) = coroutineScope {
        prestadores.map { p ->
            async {
                val endereco = p.enderecoCompleto
                if (endereco != "Endereço não informado") {
                    localizacaoRepository.distanciaKmAte(latitude, longitude, endereco)?.let { p.distancia = it }
                }
            }
        }.awaitAll()
    }

    private fun aoEnviarLocalizacao(prestador: Prestador) {
        // Segundo toque (já está enviando): para e remove do prestador.
        if (prestador.localizacaoEnviada) {
            pararDeEnviarLocalizacao(prestador)
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enviarMinhaLocalizacao(prestador)
        } else {
            enviarLocalizacaoPara = prestador
            permissaoParaEnviarLocalizacao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Apaga a localização da solicitação: o botão do prestador volta a ficar
    // cinza na hora (ele está escutando a solicitação em tempo real).
    private fun pararDeEnviarLocalizacao(prestador: Prestador) {
        val solicitacaoId = prestador.solicitacaoId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            solicitacaoRepository.removerMinhaLocalizacao(solicitacaoId)
                .onSuccess {
                    Toast.makeText(requireContext(), "Você parou de enviar sua localização. ${prestador.nome ?: "O prestador"} não vê mais onde você está.", Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Não foi possível parar de enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Lê o GPS agora (não a última posição guardada) e grava na solicitação;
    // o prestador passa a ver o botão "Localização do Motorista".
    private fun enviarMinhaLocalizacao(prestador: Prestador) {
        val solicitacaoId = prestador.solicitacaoId ?: return
        Toast.makeText(requireContext(), "Obtendo sua localização...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val local = localizacaoRepository.localizacaoAtual().getOrElse { e ->
                Toast.makeText(requireContext(), "Erro ao obter localização: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (local == null) {
                Toast.makeText(requireContext(), "Não foi possível obter sua localização. Verifique se o GPS está ligado.", Toast.LENGTH_LONG).show()
                return@launch
            }
            latitude = local.latitude
            longitude = local.longitude
            temLocalizacao = true
            solicitacaoRepository.enviarMinhaLocalizacao(solicitacaoId, local.latitude, local.longitude)
                .onSuccess {
                    Toast.makeText(requireContext(), "Localização enviada para ${prestador.nome ?: "o prestador"}.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    // As regras recusam se a solicitação deixou de estar aceita
                    // (ex.: cancelada em outro aparelho) — explica em vez do código técnico.
                    val negado = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    val texto = if (negado) "Só é possível enviar sua localização com a solicitação aceita pelo prestador."
                    else "Erro ao enviar localização: ${e.message}"
                    Toast.makeText(requireContext(), texto, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun solicitarServico(prestador: Prestador) {
        val prestadorUid = prestador.uid ?: return
        if (authRepository.uidLogado() == null) {
            Toast.makeText(requireContext(), "Faça login como motorista primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val jaTem = solicitacaoRepository.temSolicitacaoAtivaCom(prestadorUid).getOrElse { e ->
                Toast.makeText(requireContext(), "Erro ao enviar solicitação: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (jaTem) {
                Toast.makeText(requireContext(), "Você já possui uma solicitação pendente ou aceita para este prestador.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val endereco = if (temLocalizacao) localizacaoRepository.enderecoDe(latitude, longitude) else null
            solicitacaoRepository.solicitar(prestador, latitude, longitude, endereco)
                .onSuccess {
                    Toast.makeText(requireContext(), "✅ Solicitação enviada para ${prestador.nome}", Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao enviar solicitação: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun aoSegurarPrestador(prestador: Prestador) {
        val solicitacaoId = prestador.solicitacaoId
        if (prestador.statusSolicitacao == null || solicitacaoId == null) {
            Toast.makeText(requireContext(), "Não há solicitação ativa para este prestador.", Toast.LENGTH_SHORT).show()
            return
        }
        if (prestador.statusSolicitacao == CANCELADO) {
            AlertDialog.Builder(requireContext())
                .setTitle("Excluir permanentemente")
                .setMessage("Esta solicitação já foi cancelada. Deseja excluí-la permanentemente?")
                .setPositiveButton("Sim") { _, _ -> excluir(solicitacaoId) }
                .setNegativeButton("Não", null)
                .show()
            return
        }
        confirmarCancelamento(solicitacaoId)
    }

    private fun confirmarCancelamento(solicitacaoId: String) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_cancelar_solicitacao, null)
        val checkBox = view.findViewById<CheckBox>(R.id.checkBoxConfirmacaoCancelamento)
        val edtSenha = view.findViewById<EditText>(R.id.edtSenhaCancelamento)
        AlertDialog.Builder(requireContext())
            .setTitle("Cancelar solicitação")
            .setView(view)
            .setPositiveButton("Cancelar solicitação") { _, _ ->
                val senha = edtSenha.text.toString().trim()
                when {
                    !checkBox.isChecked -> Toast.makeText(requireContext(), "Marque a checkbox para confirmar.", Toast.LENGTH_SHORT).show()
                    senha.isEmpty() -> Toast.makeText(requireContext(), "Digite sua senha.", Toast.LENGTH_SHORT).show()
                    else -> cancelar(solicitacaoId, senha)
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    // Cancela só a solicitação deste card (antes cancelava todas as
    // solicitações já feitas com o prestador, inclusive as antigas).
    private fun cancelar(solicitacaoId: String, senha: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (authRepository.reautenticar(senha).isFailure) {
                Toast.makeText(requireContext(), "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            solicitacaoRepository.atualizarStatus(solicitacaoId, CANCELADO)
                .onSuccess {
                    Toast.makeText(requireContext(), "Solicitação cancelada com sucesso.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    Toast.makeText(requireContext(), "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
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
