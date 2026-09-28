package com.cjstudio.sosestrada

// Token FCM do aparelho, usado pelas Cloud Functions (functions/index.js:
// notificarNovaSolicitacao, notificarRespostaPrestador, notificarMensagemSos)
// pra saber pra qual aparelho mandar cada push. Fire-and-forget: nunca trava
// login nem abertura de tela por causa de notificação.
interface INotificacaoRepository {
    // Grava em fcmTokens/{uid} — coleção separada (e não um campo em
    // motoristas/prestadores) porque "prestadores" é lido por qualquer
    // usuário logado; o token não deve ficar exposto assim.
    fun registrarToken(uid: String?)

    // No "Sair": o aparelho deixa de receber os avisos dessa conta (senão
    // quem entrasse com outra conta no mesmo celular receberia os dela).
    // Precisa rodar ANTES do signOut — as regras exigem estar logado como o dono.
    suspend fun removerToken(uid: String?)
}
