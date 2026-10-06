package com.cjstudio.sosestrada

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UserChoiceBillingListener

interface GooglePlayBillingCallback {
    // Pagou pelo Google Play: falta confirmar no servidor
    // (confirmarCompraGooglePlayPrestador) antes de liberar o plano.
    fun onGooglePlayPurchaseCompleted(purchase: Purchase)

    // Escolheu a opção alternativa (Mercado Pago) na tela do Google: segue
    // o checkout de sempre, levando o token pro servidor reportar ao Google.
    fun onUserChoseAlternativeBilling(externalTransactionToken: String)

    fun onBillingError(mensagem: String)
}

// Play Billing Library, mesmo desenho do Caronas/Match. No Brasil o Google
// exige "User Choice Billing" pra manter o Mercado Pago: a escolha Google
// Play x Mercado Pago aparece na própria tela do Google.
class GooglePlayBillingManager(
    context: Context,
    private val callback: GooglePlayBillingCallback
) {
    private var estaConectado = false
    private val produtosDisponiveis = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { callback.onGooglePlayPurchaseCompleted(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                Log.e(TAG, "Erro na compra via Google Play: ${billingResult.debugMessage}")
                callback.onBillingError(billingResult.debugMessage)
            }
        }
    }

    private val userChoiceBillingListener = UserChoiceBillingListener { detalhes ->
        callback.onUserChoseAlternativeBilling(detalhes.externalTransactionToken)
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableUserChoiceBilling(userChoiceBillingListener)
        .build()

    // O produto deste plano existe no Google Play? (app instalado pela loja
    // e produtos criados no Play Console.)
    fun temProduto(produto: String) = estaConectado && produtosDisponiveis.containsKey(produto)

    // Idempotente: já conectado chama aoConectar() na hora. aoConectar
    // também é chamado quando a conexão falha (o app segue pelo Mercado Pago).
    fun conectar(aoConectar: () -> Unit) {
        if (estaConectado) {
            aoConectar()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    carregarProdutos { estaConectado = true; aoConectar() }
                } else {
                    Log.w(TAG, "Play Billing indisponível: ${billingResult.debugMessage}")
                    aoConectar()
                }
            }

            override fun onBillingServiceDisconnected() {
                estaConectado = false
            }
        })
    }

    private fun carregarProdutos(aoTerminar: () -> Unit) {
        val produtos = IAssinaturaRepository.PRODUTOS_GOOGLE_PLAY.values.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(produtos).build()
        billingClient.queryProductDetailsAsync(params) { billingResult, detalhes ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                produtosDisponiveis.clear()
                detalhes.forEach { produtosDisponiveis[it.productId] = it }
                if (detalhes.isEmpty()) {
                    Log.w(TAG, "Nenhum produto no Play Console (plano_prestador_trimestral/semestral).")
                }
            } else {
                Log.e(TAG, "Erro ao consultar produtos no Google Play: ${billingResult.debugMessage}")
            }
            aoTerminar()
        }
    }

    // Abre a tela do Google (no Brasil, com a escolha Google Play x Mercado
    // Pago). O uid vai como obfuscatedAccountId: o servidor confere que a
    // compra é da mesma conta.
    fun iniciarCompra(activity: Activity, produto: String, uid: String) {
        val detalhes = produtosDisponiveis[produto]
        if (detalhes == null) {
            callback.onBillingError("Plano indisponível no Google Play no momento.")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(detalhes).build()))
            .setObfuscatedAccountId(uid)
            .build()
        val resultado = billingClient.launchBillingFlow(activity, params)
        if (resultado.responseCode != BillingClient.BillingResponseCode.OK) {
            callback.onBillingError(resultado.debugMessage)
        }
    }

    // Reconsulta compras pagas (Pix/boleto que estavam pendentes). O
    // servidor é idempotente, reprocessar não tem problema.
    fun verificarComprasPendentes() {
        if (!estaConectado) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { billingResult, compras ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            compras.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { callback.onGooglePlayPurchaseCompleted(it) }
        }
    }

    fun encerrar() {
        billingClient.endConnection()
    }

    companion object {
        private const val TAG = "GooglePlayBilling"
    }
}
