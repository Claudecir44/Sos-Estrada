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
    // Cópia do sexo do motorista (o prestador não lê o cadastro dele);
    // firestore.rules confere com o cadastro.
    var motoristaSexo: String? = null,
    var status: String? = null, // pendente, aceito, recusado, finalizado
    var timestamp: Date? = null,
    var latitudeMotorista: Double = 0.0,
    var longitudeMotorista: Double = 0.0,
    var enderecoMotorista: String? = null,
    // Localização atual enviada pelo motorista no botão "Enviar Minha
    // Localização" (null enquanto ele não enviar). Vira o botão
    // "Localização do Motorista" no card do prestador.
    var latitudeCompartilhada: Double? = null,
    var longitudeCompartilhada: Double? = null,
    var localizacaoCompartilhadaEm: Date? = null,
    // Alerta: true a cada envio do motorista; o prestador zera ao abrir o mapa.
    var localizacaoNaoVistaPrestador: Boolean = false,
    // Contadores de mensagens não lidas por lado, mantidos pelo chat
    // (incrementados ao enviar, zerados ao abrir o chat do respectivo lado).
    var naoLidasMotorista: Int = 0,
    var naoLidasPrestador: Int = 0,
    // Alertas (bolinha no painel, ícone do app): nasce true na criação e o
    // prestador zera ao abrir "Atender solicitações".
    var novaParaPrestador: Boolean = false,
    // Vira true quando o prestador aceita/recusa; o motorista zera ao abrir
    // "Preciso de socorro".
    var respostaNaoVistaMotorista: Boolean = false
)
