package com.cjstudio.sosestrada

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Conversa 1-a-1 do Chat Admin (conversasAdmin/{id}), entre admins e
// colaboradores — mesmo modelo do Caronas. Os dois lados são genéricos
// (admin1/admin2); o id é o par de uids em ordem (ChatAdminRepository).
// Serializable pra ir inteira na Intent do ChatAdminActivity.
data class ConversaAdmin(
    @get:Exclude var id: String? = null,
    var participantes: List<String>? = null,
    var admin1Id: String? = null,
    var admin1Nome: String? = null,
    var admin2Id: String? = null,
    var admin2Nome: String? = null,
    var ultimaMensagem: String? = null,
    @ServerTimestamp var ultimoTimestamp: Date? = null,
    var ultimoRemetenteId: String? = null,
    var naoLidas1: Int = 0,
    var naoLidas2: Int = 0
) : java.io.Serializable {
    fun idOutroAdmin(meuId: String?) = if (admin1Id == meuId) admin2Id else admin1Id
    fun nomeOutroAdmin(meuId: String?) = if (admin1Id == meuId) admin2Nome else admin1Nome
    fun naoLidasParaMim(meuId: String?) = if (admin1Id == meuId) naoLidas1 else naoLidas2
}

// Mensagem do Chat Admin (conversasAdmin/{id}/mensagens/{id}). "conteudo"
// fica cifrado (ChatAdminCryptoUtil). Apagar "só pra mim" marca
// deletadoParaRemetente/Destinatario; "pra todos" (só quem mandou)
// sobrescreve o conteúdo.
data class MensagemChatAdmin(
    @get:Exclude var id: String? = null,
    @get:Exclude var conversaId: String? = null,
    var remetenteId: String? = null,
    var remetenteNome: String? = null,
    var destinatarioId: String? = null,
    var destinatarioNome: String? = null,
    var conteudo: String? = null,
    @ServerTimestamp var timestamp: Date? = null,
    var lida: Boolean = false,
    var tipo: String = "texto",
    var deletadoParaTodos: Boolean = false,
    var deletadoParaRemetente: Boolean = false,
    var deletadoParaDestinatario: Boolean = false
) {
    fun deletadaParaMim(meuId: String?): Boolean =
        deletadoParaTodos || (remetenteId == meuId && deletadoParaRemetente) || (destinatarioId == meuId && deletadoParaDestinatario)
}

// Admin/colaborador que aparece na lista de "Nova conversa".
data class ContatoAdmin(
    val id: String,
    val nome: String,
    val email: String?,
    val foto: String?,
    val colaborador: Boolean
)
