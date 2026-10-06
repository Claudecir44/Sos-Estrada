package com.cjstudio.sosestrada

import kotlinx.coroutines.flow.Flow

// Chat Admin: conversas 1-a-1 entre admins e colaboradores, iguais às do
// Caronas — lista de conversas, nova conversa, mandar mensagem, lida/não
// lida, apagar mensagem (só pra mim / pra todos), apagar conversa inteira e
// total de não lidas no chip do painel. Funciona junto com o painel web.
interface IChatAdminRepository {

    fun meuUid(): String?

    // Conversas do admin logado, mais recente primeiro.
    fun escutarMinhasConversas(): Flow<List<ConversaAdmin>>

    // Outros admins e colaboradores (pra "Nova conversa").
    suspend fun listarContatos(): Result<List<ContatoAdmin>>

    // Acha a conversa com esse admin ou cria uma nova.
    suspend fun buscarOuCriarConversa(contato: ContatoAdmin): Result<ConversaAdmin>

    // Mensagens já decifradas, mais antiga primeiro.
    fun escutarMensagens(conversaId: String): Flow<List<MensagemChatAdmin>>

    suspend fun enviarMensagem(conversa: ConversaAdmin, texto: String): Result<Unit>

    suspend fun marcarConversaComoLida(conversa: ConversaAdmin): Result<Unit>

    suspend fun apagarMensagemParaMim(mensagem: MensagemChatAdmin): Result<Unit>

    // Só quem mandou.
    suspend fun apagarMensagemParaTodos(mensagem: MensagemChatAdmin): Result<Unit>

    // Todas as mensagens + a conversa (some pros dois lados).
    suspend fun apagarConversa(conversa: ConversaAdmin): Result<Unit>

    // Soma das não lidas em todas as conversas (chip "💬 Chat Admin").
    fun escutarTotalNaoLidas(): Flow<Int>

    // Token de push do APP ADMIN em admins/{uid}.fcmToken — separado do
    // fcmTokens/{uid} do app de motorista/prestador da mesma conta.
    fun registrarTokenAdmin()
}
