package com.cjstudio.sosestrada

import java.util.Date

// Documento solicitacoes/{id}/mensagens/{id} do chat motorista <-> prestador.
data class Mensagem(
    var id: String? = null,
    var remetenteUid: String? = null,
    var remetenteTipo: String? = null, // "motorista" ou "prestador"
    var texto: String? = null,
    var imagemUrl: String? = null,
    var timestamp: Date? = null,
    var lida: Boolean = false
)
