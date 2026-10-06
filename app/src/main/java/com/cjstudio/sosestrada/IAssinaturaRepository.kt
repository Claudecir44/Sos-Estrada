package com.cjstudio.sosestrada

import kotlinx.coroutines.flow.Flow
import kotlin.math.ceil

// Assinatura do prestador (Google Play ou Mercado Pago). Os campos vivem no próprio
// prestadores/{uid} e só as Cloud Functions gravam neles.
interface IAssinaturaRepository {
    suspend fun buscarMinhaAssinatura(): Result<StatusAssinatura>

    // Mudanças da assinatura em tempo real — é assim que a tela sabe que o
    // webhook do Mercado Pago confirmou o pagamento.
    fun escutarMinhaAssinatura(): Flow<StatusAssinatura>

    // Cria a preferência de pagamento no servidor pro plano escolhido
    // (PLANO_TRIMESTRAL ou PLANO_SEMESTRAL — preço e duração são decididos
    // lá, em PLANOS de functions/index.js) e devolve o link do checkout.
    // externalTransactionToken: veio da tela de escolha do Google (User Choice)
    // — o servidor reporta a transação ao Google Play depois do pagamento.
    suspend fun criarCheckout(plano: String, externalTransactionToken: String? = null): Result<String>

    // Confirma no servidor uma compra feita pelo Google Play
    // (confirmarCompraGooglePlayPrestador). true = ainda pendente (Pix/boleto).
    suspend fun confirmarCompraGooglePlay(purchaseToken: String, productId: String): Result<Boolean>

    companion object {
        const val PLANO_TRIMESTRAL = "trimestral"
        const val PLANO_SEMESTRAL = "semestral"
        // Produtos avulsos no Play Console — mesmos IDs de
        // PRODUTOS_GOOGLE_PLAY em functions/index.js.
        val PRODUTOS_GOOGLE_PLAY = mapOf(
            PLANO_TRIMESTRAL to "plano_prestador_trimestral",
            PLANO_SEMESTRAL to "plano_prestador_semestral"
        )
        // Mesmo prazo de TRIAL_DIAS em functions/index.js.
        const val TRIAL_DIAS = 60
    }
}

data class StatusAssinatura(
    val status: String, // "trial", "ativa" ou "expirada"
    val dataCadastro: Long?,
    val expiraEm: Long?,
    // false = plano vencido (sumiu da busca); null = cadastro antigo.
    val ativo: Boolean? = null,
    val bloqueado: Boolean = false,
    // Dias do último plano pago (o "90" de "Plano pago 45/90 dias").
    val diasTotal: Int? = null,
    // CPF/CNPJ que já tinha usado o período grátis: só ativa pagando.
    val trialNegado: Boolean = false
)

// O que o painel do prestador mostra embaixo das Configurações.
data class ResumoPlano(val cadastroAtivo: Boolean, val plano: String)

private const val DIA_MS = 24 * 60 * 60 * 1000L

// Dias que faltam, arredondando pra cima — a mesma conta de
// avisarVencimentoPrestadores (functions/index.js), que manda os avisos de
// 5 e 2 dias.
private fun diasAte(fim: Long, agora: Long) = ceil((fim - agora).toDouble() / DIA_MS).toInt().coerceAtLeast(0)

fun StatusAssinatura.resumo(agora: Long = System.currentTimeMillis()): ResumoPlano {
    val ativoAgora = !bloqueado && ativo != false && status != "expirada"
    val trial = IAssinaturaRepository.TRIAL_DIAS
    val plano = when {
        status == "ativa" -> {
            val total = diasTotal ?: 90
            "Plano pago %02d/%d dias".format(expiraEm?.let { diasAte(it, agora) } ?: 0, total)
        }
        status == "trial" -> {
            val restam = dataCadastro?.let { diasAte(it + trial * DIA_MS, agora).coerceAtMost(trial) } ?: trial
            "Plano free %02d/%d dias".format(restam, trial)
        }
        trialNegado && expiraEm == null -> "Sem plano free: assine"
        expiraEm != null -> "Plano pago 00/%d dias".format(diasTotal ?: 90)
        else -> "Plano free 00/%d dias".format(trial)
    }
    return ResumoPlano(ativoAgora, plano)
}
