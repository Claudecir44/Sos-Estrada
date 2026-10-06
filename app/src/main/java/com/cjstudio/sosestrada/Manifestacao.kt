package com.cjstudio.sosestrada

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Reclamação, sugestão ou denúncia enviada pelo motorista/prestador
// (Configurações ou "Denunciar" no chat), igual ao Caronas. O admin vê no
// chip "📝 Sugestões" do painel e responde por e-mail
// (responderManifestacao em functions/index.js).
data class Manifestacao(
    var id: String? = null,
    var uid: String? = null,
    var nome: String? = null,
    var email: String? = null,
    var tipo: String? = null,
    var mensagem: String? = null,
    // Só na denúncia contra outro usuário.
    var motivo: String? = null,
    var denunciadoUid: String? = null,
    var denunciadoNome: String? = null,
    var origem: String? = null,
    var status: String = STATUS_NOVA,
    var resposta: String? = null,
    var respondidoEm: Date? = null,
    @ServerTimestamp
    var criadoEm: Date? = null
) {
    companion object {
        const val RECLAMACAO = "reclamacao"
        const val SUGESTAO = "sugestao"
        const val DENUNCIA = "denuncia"

        const val STATUS_NOVA = "nova"
        const val STATUS_RESPONDIDA = "respondida"
        const val STATUS_ARQUIVADA = "arquivada"

        fun rotuloTipo(tipo: String?) = when (tipo) {
            RECLAMACAO -> "😠 Reclamação"
            SUGESTAO -> "💡 Sugestão"
            DENUNCIA -> "🚩 Denúncia"
            else -> "📝 Mensagem"
        }
    }
}
