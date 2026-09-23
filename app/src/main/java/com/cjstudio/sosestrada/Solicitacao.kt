package com.cjstudio.sosestrada

import java.util.Date

// Documento solicitacoes/{id}: pedido de socorro de um motorista a um prestador.
data class Solicitacao(
    var id: String? = null,
    var motoristaUid: String? = null,
    var prestadorUid: String? = null,
    var prestadorNome: String? = null,
    var motoristaNome: String? = null,
    var motoristaTelefone: String? = null,
    var motoristaVeiculo: String? = null,
    var motoristaPlaca: String? = null,
    var status: String? = null, // pendente, aceito, recusado, finalizado
    var timestamp: Date? = null,
    var latitudeMotorista: Double = 0.0,
    var longitudeMotorista: Double = 0.0,
    var enderecoMotorista: String? = null,
    // Contadores de mensagens não lidas por lado, mantidos pelo chat
    // (incrementados ao enviar, zerados ao abrir o chat do respectivo lado).
    var naoLidasMotorista: Int = 0,
    var naoLidasPrestador: Int = 0
)
