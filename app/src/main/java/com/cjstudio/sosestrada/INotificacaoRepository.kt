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
}
