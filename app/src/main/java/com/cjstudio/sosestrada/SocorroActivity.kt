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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.CANCELADO
import com.cjstudio.sosestrada.databinding.ActivitySocorroBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// Busca de socorro do motorista: lista os prestadores ativos (com distância,
// quando há GPS), pede o serviço, cancela e exclui solicitações.
@AndroidEntryPoint
class SocorroActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var prestadorRepository: IPrestadorRepository

    @Inject
    lateinit var solicitacaoRepository: ISolicitacaoRepository

    @Inject
    lateinit var localizacaoRepository: ILocalizacaoRepository

    private lateinit var binding: ActivitySocorroBinding
    private lateinit var adapter: PrestadorAdapter

    private var latitude = 0.0
    private var longitude = 0.0
    private var temLocalizacao = false
    private var primeiraCargaFeita = false

    private val permissaoLocalizacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            obterLocalizacaoECarregar()
        } else {
            Toast.makeText(this, "Permissão de localização negada. A distância não será calculada.", Toast.LENGTH_LONG).show()
            carregarPrestadores()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySocorroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PrestadorAdapter(aoSolicitar = ::solicitarServico, aoSegurar = ::aoSegurarPrestador)
        binding.rvPrestadoresSocorro.layoutManager = LinearLayoutManager(this)
        binding.rvPrestadoresSocorro.adapter = adapter
        binding.edtPesquisa.doOnTextChanged { texto, _, _, _ -> adapter.filtrar(texto?.toString().orEmpty()) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obterLocalizacaoECarregar()
        } else {
            permissaoLocalizacao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Volta do chat: recarrega status e contadores de mensagens.
    override fun onResume() {
        super.onResume()
        if (primeiraCargaFeita) carregarPrestadores()
    }

    private fun obterLocalizacaoECarregar() {
        lifecycleScope.launch {
            localizacaoRepository.ultimaLocalizacao()
                .onSuccess { local ->
                    if (local != null) {
                        latitude = local.latitude
                        longitude = local.longitude
                        temLocalizacao = true
                    } else {
                        Toast.makeText(this@SocorroActivity, "Não foi possível obter a localização. Verifique o GPS.", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { e ->
                    Toast.makeText(this@SocorroActivity, "Erro ao obter localização: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            carregarPrestadores()
        }
    }

    private fun carregarPrestadores() {
        primeiraCargaFeita = true
        binding.progressBarSocorro.visibility = View.VISIBLE
        binding.tvEmptySocorro.visibility = View.GONE

        lifecycleScope.launch {
            val prestadores = prestadorRepository.listarAtivos().getOrElse { e ->
                binding.progressBarSocorro.visibility = View.GONE
                binding.tvEmptySocorro.visibility = View.VISIBLE
                Toast.makeText(this@SocorroActivity, "Erro ao carregar prestadores: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (temLocalizacao) calcularDistancias(prestadores)

            // Sem as solicitações, a lista aparece do mesmo jeito (só sem status).
            val solicitacoes = solicitacaoRepository.minhasSolicitacoesPorPrestador().getOrDefault(emptyMap())
            for (p in prestadores) {
                val solicitacao = solicitacoes[p.uid]
                p.statusSolicitacao = solicitacao?.status
                p.solicitacaoId = solicitacao?.id
                p.naoLidasMotorista = solicitacao?.naoLidasMotorista ?: 0
            }

            binding.progressBarSocorro.visibility = View.GONE
            binding.tvEmptySocorro.visibility = if (prestadores.isEmpty()) View.VISIBLE else View.GONE
            adapter.atualizarLista(prestadores)
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

    private fun solicitarServico(prestador: Prestador) {
        val prestadorUid = prestador.uid ?: return
        if (authRepository.uidLogado() == null) {
            Toast.makeText(this, "Faça login como motorista primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val jaTem = solicitacaoRepository.temSolicitacaoAtivaCom(prestadorUid).getOrElse { e ->
                Toast.makeText(this@SocorroActivity, "Erro ao enviar solicitação: ${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (jaTem) {
                Toast.makeText(this@SocorroActivity, "Você já possui uma solicitação pendente ou aceita para este prestador.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val endereco = if (temLocalizacao) localizacaoRepository.enderecoDe(latitude, longitude) else null
            solicitacaoRepository.solicitar(prestador, latitude, longitude, endereco)
                .onSuccess {
                    Toast.makeText(this@SocorroActivity, "✅ Solicitação enviada para ${prestador.nome}", Toast.LENGTH_LONG).show()
                    carregarPrestadores()
                }
                .onFailure { e ->
                    Toast.makeText(this@SocorroActivity, "Erro ao enviar solicitação: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun aoSegurarPrestador(prestador: Prestador) {
        val solicitacaoId = prestador.solicitacaoId
        if (prestador.statusSolicitacao == null || solicitacaoId == null) {
            Toast.makeText(this, "Não há solicitação ativa para este prestador.", Toast.LENGTH_SHORT).show()
            return
        }
        if (prestador.statusSolicitacao == CANCELADO) {
            AlertDialog.Builder(this)
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
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_cancelar_solicitacao, null)
        val checkBox = view.findViewById<CheckBox>(R.id.checkBoxConfirmacaoCancelamento)
        val edtSenha = view.findViewById<EditText>(R.id.edtSenhaCancelamento)
        AlertDialog.Builder(this)
            .setTitle("Cancelar solicitação")
            .setView(view)
            .setPositiveButton("Cancelar solicitação") { _, _ ->
                val senha = edtSenha.text.toString().trim()
                when {
                    !checkBox.isChecked -> Toast.makeText(this, "Marque a checkbox para confirmar.", Toast.LENGTH_SHORT).show()
                    senha.isEmpty() -> Toast.makeText(this, "Digite sua senha.", Toast.LENGTH_SHORT).show()
                    else -> cancelar(solicitacaoId, senha)
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    // Cancela só a solicitação deste card (antes cancelava todas as
    // solicitações já feitas com o prestador, inclusive as antigas).
    private fun cancelar(solicitacaoId: String, senha: String) {
        lifecycleScope.launch {
            if (authRepository.reautenticar(senha).isFailure) {
                Toast.makeText(this@SocorroActivity, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            solicitacaoRepository.atualizarStatus(solicitacaoId, CANCELADO)
                .onSuccess {
                    Toast.makeText(this@SocorroActivity, "Solicitação cancelada com sucesso.", Toast.LENGTH_SHORT).show()
                    carregarPrestadores()
                }
                .onFailure { e ->
                    Toast.makeText(this@SocorroActivity, "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun excluir(solicitacaoId: String) {
        lifecycleScope.launch {
            solicitacaoRepository.excluir(solicitacaoId)
                .onSuccess {
                    Toast.makeText(this@SocorroActivity, "Solicitação excluída permanentemente.", Toast.LENGTH_SHORT).show()
                    carregarPrestadores()
                }
                .onFailure { e ->
                    Toast.makeText(this@SocorroActivity, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

}
