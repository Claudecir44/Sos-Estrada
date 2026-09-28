package com.cjstudio.sosestrada

// Documento pagamentos/{id}: um por pagamento aprovado da assinatura do
// prestador (gravado pelo webhook paymentWebhookPrestador em
// functions/index.js). Base do Financeiro do app admin e do painel web —
// mesmo papel do pagamentosMotorista do Caronas. Datas em millis.
data class PagamentoPrestador(
    var id: String? = null,
    var prestadorId: String? = null,
    var prestadorNome: String? = null,
    var prestadorEmail: String? = null,
    var valor: Double = 0.0,
    var diasValidade: Int? = null,
    var dataCompra: Long? = null,
    var expiraEm: Long? = null,
    var mercadoPagoPaymentId: String? = null
)
