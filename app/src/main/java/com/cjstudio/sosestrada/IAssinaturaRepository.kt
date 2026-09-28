package com.cjstudio.sosestrada

import kotlinx.coroutines.flow.Flow

// Assinatura anual do prestador (Mercado Pago). Os campos vivem no próprio
// prestadores/{uid} e só as Cloud Functions gravam neles.
interface IAssinaturaRepository {
    suspend fun buscarMinhaAssinatura(): Result<StatusAssinatura>

    // Mudanças da assinatura em tempo real — é assim que a tela sabe que o
    // webhook do Mercado Pago confirmou o pagamento.
    fun escutarMinhaAssinatura(): Flow<StatusAssinatura>

    // Cria a preferência de pagamento no servidor pro plano escolhido
    // (PLANO_TRIMESTRAL ou PLANO_SEMESTRAL — preço e duração são decididos
    // lá, em PLANOS de functions/index.js) e devolve o link do checkout.
    suspend fun criarCheckout(plano: String): Result<String>

    companion object {
        const val PLANO_TRIMESTRAL = "trimestral"
        const val PLANO_SEMESTRAL = "semestral"
    }
}

data class StatusAssinatura(
    val status: String, // "trial", "ativa" ou "expirada"
    val dataCadastro: Long?,
    val expiraEm: Long?
)
