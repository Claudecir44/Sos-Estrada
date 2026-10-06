package com.cjstudio.sosestrada

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Avaliação de um atendimento, nos dois sentidos (motorista -> prestador e
// prestador -> motorista), mesmo modelo do Caronas. ID do documento é
// "{solicitacaoId}_{avaliadorUid}": a mesma pessoa não avalia o mesmo
// atendimento duas vezes (a segunda gravação vira update, e as regras não
// deixam). A média de cada um fica em notasUsuarios/{uid}, calculada pelo
// servidor (atualizarNotaUsuario em functions/index.js).
data class Avaliacao(
    var solicitacaoId: String? = null,
    var avaliadorUid: String? = null,
    var avaliadoUid: String? = null,
    var nota: Int = 0,
    var comentario: String? = null,
    @ServerTimestamp
    var criadoEm: Date? = null
)

// Média e quantidade de avaliações recebidas por alguém.
data class NotaUsuario(val media: Double, val total: Int)
