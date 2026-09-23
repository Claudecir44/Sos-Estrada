package com.cjstudio.sosestrada

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityAssinaturaBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Tela de assinatura do prestador: 180 dias grátis a partir do cadastro
// (aoRegistrarPrestador, Cloud Function), depois um plano anual de
// R$49,90/365 dias pra manter o cadastro visível na busca do motorista
// (a busca só lista prestadores com ativo==true). A confirmação de
// pagamento NUNCA vem desta tela sozinha — vem do webhook
// (paymentWebhookPrestador) escrevendo no Firestore; por isso a tela
// escuta a assinatura em vez de assumir sucesso ao voltar do checkout
// (mesmo desenho do AssinaturaActivity do Match).
@AndroidEntryPoint
class AssinaturaActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var assinaturaRepository: IAssinaturaRepository

    private lateinit var binding: ActivityAssinaturaBinding
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Validade de antes de abrir o checkout — só um valor NOVO e futuro
    // vindo pelo listener conta como pagamento confirmado.
    private var expiraEmAntesDaCompra = 0L
    private var escutaConfirmacao: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authRepository.uidLogado() == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityAssinaturaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltarAssinatura.setOnClickListener { finish() }
        binding.btnAssinar.setOnClickListener { iniciarPagamento() }

        carregarStatus()
    }

    private fun carregarStatus() {
        binding.progressBarAssinatura.visibility = View.VISIBLE
        lifecycleScope.launch {
            assinaturaRepository.buscarMinhaAssinatura()
                .onSuccess { exibirStatus(it) }
                .onFailure { e ->
                    Toast.makeText(this@AssinaturaActivity, "Erro ao carregar assinatura: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            binding.progressBarAssinatura.visibility = View.GONE
        }
    }

    private fun exibirStatus(assinatura: StatusAssinatura) {
        val expiraEm = assinatura.expiraEm
        expiraEmAntesDaCompra = expiraEm ?: 0L

        when (assinatura.status) {
            "ativa" -> {
                binding.tvStatusAssinatura.text = "✅ Assinatura ativa"
                binding.tvDetalheAssinatura.text = if (expiraEm != null) "Válida até ${formatoData.format(Date(expiraEm))}" else ""
                binding.btnAssinar.text = "Renovar plano anual (R$ 49,90)"
            }
            "expirada" -> {
                binding.tvStatusAssinatura.text = "⛔ Cadastro inativo"
                binding.tvDetalheAssinatura.text =
                    "Seu período grátis ou sua assinatura venceu. Seu cadastro não aparece mais para motoristas até renovar."
                binding.btnAssinar.text = "Assinar plano anual (R$ 49,90)"
            }
            else -> { // trial
                val diasRestantes = assinatura.dataCadastro?.let {
                    val diasPassados = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it)
                    (TRIAL_DIAS - diasPassados).coerceAtLeast(0)
                }
                binding.tvStatusAssinatura.text = "🎁 Período grátis"
                binding.tvDetalheAssinatura.text = if (diasRestantes != null) {
                    "$diasRestantes dia(s) restantes de $TRIAL_DIAS dias grátis"
                } else {
                    "Aproveitando o período de testes"
                }
                binding.btnAssinar.text = "Assinar plano anual (R$ 49,90)"
            }
        }
    }

    private fun iniciarPagamento() {
        binding.progressBarAssinatura.visibility = View.VISIBLE
        binding.btnAssinar.isEnabled = false
        lifecycleScope.launch {
            assinaturaRepository.criarCheckout()
                .onSuccess { initPoint ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(initPoint)))
                    esperarConfirmacao()
                }
                .onFailure { e ->
                    Log.e(TAG, "Erro ao criar preferência de pagamento", e)
                    Toast.makeText(this@AssinaturaActivity, "Erro ao iniciar pagamento: ${e.message}", Toast.LENGTH_LONG).show()
                }
            binding.progressBarAssinatura.visibility = View.GONE
            binding.btnAssinar.isEnabled = true
        }
    }

    // Funciona mesmo que o prestador nunca volte pelo deep link — só depende
    // do Firestore, não do redirecionamento.
    private fun esperarConfirmacao() {
        binding.layoutAguardandoConfirmacao.visibility = View.VISIBLE
        escutaConfirmacao?.cancel()
        escutaConfirmacao = lifecycleScope.launch {
            val confirmada = assinaturaRepository.escutarMinhaAssinatura().first { assinatura ->
                val expiraEm = assinatura.expiraEm ?: return@first false
                expiraEm > System.currentTimeMillis() && expiraEm != expiraEmAntesDaCompra
            }
            binding.layoutAguardandoConfirmacao.visibility = View.GONE
            Toast.makeText(this@AssinaturaActivity, "🎉 Assinatura ativada com sucesso!", Toast.LENGTH_LONG).show()
            exibirStatus(confirmada)
        }
    }

    // Retorno do checkout via deep link (sosestrada://payment_success/...,
    // ver AndroidManifest.xml + back_urls em criarPreferenciaPagamentoPrestador).
    // Só traz a tela de volta pro topo — quem decide se o pagamento foi
    // aprovado é sempre a escuta acima.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        private const val TAG = "AssinaturaActivity"
        private const val TRIAL_DIAS = 180
    }
}
