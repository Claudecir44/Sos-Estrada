package com.cjstudio.sosestrada

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.Purchase
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

// Tela de assinatura do prestador: 60 dias grátis a partir do cadastro
// (aoRegistrarPrestador, Cloud Function), depois o plano Trimestral
// (R$79,90/90 dias) ou Semestral (R$129,90/180 dias) pra manter o cadastro
// visível na busca do motorista
// (a busca só lista prestadores com ativo==true). A confirmação de
// pagamento NUNCA vem desta tela sozinha — vem do webhook
// (paymentWebhookPrestador) escrevendo no Firestore; por isso a tela
// escuta a assinatura em vez de assumir sucesso ao voltar do checkout
// (mesmo desenho do AssinaturaActivity do Match).
@AndroidEntryPoint
class AssinaturaActivity : AppCompatActivity(), GooglePlayBillingCallback {

    // Google Play Billing (User Choice): a tela do Google oferece pagar pelo
    // Google Play ou pelo Mercado Pago. Sem o produto no Google Play (app
    // instalado fora da loja ou produto ainda não criado), vai direto pro
    // Mercado Pago.
    private lateinit var billing: GooglePlayBillingManager
    private var planoEmAndamento: String? = null

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
        binding.btnAssinarTrimestral.setOnClickListener { iniciarPagamento(IAssinaturaRepository.PLANO_TRIMESTRAL) }
        binding.btnAssinarSemestral.setOnClickListener { iniciarPagamento(IAssinaturaRepository.PLANO_SEMESTRAL) }
        billing = GooglePlayBillingManager(this, this)

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
                textoBotoes("Renovar")
            }
            "expirada" -> {
                binding.tvStatusAssinatura.text = "⛔ Cadastro inativo"
                binding.tvDetalheAssinatura.text = if (assinatura.trialNegado && expiraEm == null) {
                    "Este CPF/CNPJ já usou o período grátis. Seu cadastro fica ativo assim que você assinar um plano."
                } else {
                    "Seu período grátis ou sua assinatura venceu. Seu cadastro não aparece mais para motoristas até renovar. " +
                        "Sem renovar em 6 meses, o cadastro é removido."
                }
                textoBotoes("Assinar")
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
                textoBotoes("Assinar")
            }
        }
    }

    // Com assinatura ativa, os botões viram "Renovar" (os dias que faltam
    // são somados ao novo prazo — ver paymentWebhookPrestador).
    private fun textoBotoes(acao: String) {
        binding.btnAssinarTrimestral.text = acao
        binding.btnAssinarSemestral.text = acao
    }

    private fun habilitarBotoes(sim: Boolean) {
        binding.btnAssinarTrimestral.isEnabled = sim
        binding.btnAssinarSemestral.isEnabled = sim
    }

    override fun onResume() {
        super.onResume()
        // Pix/boleto pago enquanto a tela estava fechada.
        if (::billing.isInitialized) billing.conectar { billing.verificarComprasPendentes() }
    }

    override fun onDestroy() {
        if (::billing.isInitialized) billing.encerrar()
        super.onDestroy()
    }

    private fun iniciarPagamento(plano: String) {
        val produto = IAssinaturaRepository.PRODUTOS_GOOGLE_PLAY.getValue(plano)
        val uid = authRepository.uidLogado() ?: return
        planoEmAndamento = plano
        habilitarBotoes(false)
        billing.conectar {
            runOnUiThread {
                habilitarBotoes(true)
                if (billing.temProduto(produto)) billing.iniciarCompra(this, produto, uid)
                else abrirMercadoPago(plano, null)
            }
        }
    }

    // ---- GooglePlayBillingCallback ----
    override fun onGooglePlayPurchaseCompleted(purchase: Purchase) {
        val produto = purchase.products.firstOrNull() ?: return
        lifecycleScope.launch {
            binding.progressBarAssinatura.visibility = View.VISIBLE
            assinaturaRepository.confirmarCompraGooglePlay(purchase.purchaseToken, produto)
                .onSuccess { pendente ->
                    if (pendente) {
                        Toast.makeText(this@AssinaturaActivity, "Pagamento em processamento. O plano é ativado assim que o Google confirmar.", Toast.LENGTH_LONG).show()
                    } else {
                        esperarConfirmacao()
                    }
                }
                .onFailure { e ->
                    Toast.makeText(this@AssinaturaActivity, "Não foi possível confirmar a compra: ${e.message}", Toast.LENGTH_LONG).show()
                }
            binding.progressBarAssinatura.visibility = View.GONE
        }
    }

    override fun onUserChoseAlternativeBilling(externalTransactionToken: String) {
        val plano = planoEmAndamento ?: return
        runOnUiThread { abrirMercadoPago(plano, externalTransactionToken) }
    }

    override fun onBillingError(mensagem: String) {
        runOnUiThread {
            habilitarBotoes(true)
            Toast.makeText(this, "Pagamento pelo Google Play: $mensagem", Toast.LENGTH_LONG).show()
        }
    }

    private fun abrirMercadoPago(plano: String, externalTransactionToken: String?) {
        binding.progressBarAssinatura.visibility = View.VISIBLE
        habilitarBotoes(false)
        lifecycleScope.launch {
            assinaturaRepository.criarCheckout(plano, externalTransactionToken)
                .onSuccess { initPoint ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(initPoint)))
                    esperarConfirmacao()
                }
                .onFailure { e ->
                    Log.e(TAG, "Erro ao criar preferência de pagamento", e)
                    Toast.makeText(this@AssinaturaActivity, "Erro ao iniciar pagamento: ${e.message}", Toast.LENGTH_LONG).show()
                }
            binding.progressBarAssinatura.visibility = View.GONE
            habilitarBotoes(true)
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
        private const val TRIAL_DIAS = IAssinaturaRepository.TRIAL_DIAS
    }
}
