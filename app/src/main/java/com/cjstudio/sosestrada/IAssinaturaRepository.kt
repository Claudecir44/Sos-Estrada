package com.cjstudio.sosestrada

import kotlinx.coroutines.flow.Flow

// Assinatura anual do prestador (Mercado Pago). Os campos vivem no próprio
// prestadores/{uid} e só as Cloud Functions gravam neles.
interface IAssinaturaRepository {
    suspend fun buscarMinhaAssinatura(): Result<StatusAssinatura>

    // Mudanças da assinatura em tempo real — é assim que a tela sabe que o
    // webhook do Mercado Pago confirmou o pagamento.
    fun escutarMinhaAssinatura(): Flow<StatusAssinatura>

    // Cria a preferência de pagamento no servidor (preço e duração decididos
    // lá) e devolve o link do checkout.
    suspend fun criarCheckout(): Result<String>
}

data class StatusAssinatura(
    val status: String, // "trial", "ativa" ou "expirada"
    val dataCadastro: Long?,
    val expiraEm: Long?
)
