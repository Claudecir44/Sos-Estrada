package com.cjstudio.sosestrada

// Bloqueio entre usuários e mensagens ao suporte (reclamação, sugestão,
// denúncia) — exigência do Google Play pra apps com chat entre pessoas.
interface ISegurancaRepository {
    // Bloqueio vale nos dois sentidos: os dois deixam de se ver na busca e
    // não conseguem mais pedir socorro nem trocar mensagens (firestore.rules).
    suspend fun bloquear(outroUid: String, outroNome: String?): Result<Unit>
    suspend fun desbloquear(outroUid: String): Result<Unit>
    suspend fun bloqueei(outroUid: String): Result<Boolean>

    // Quem eu bloqueei (uid -> nome), pra tela "Usuários bloqueados".
    suspend fun meusBloqueios(): Result<Map<String, String>>

    // Todos com quem existe bloqueio, em qualquer sentido (esconde da busca).
    suspend fun idsComBloqueio(): Result<Set<String>>

    suspend fun enviarManifestacao(
        tipo: String,
        mensagem: String,
        motivo: String? = null,
        denunciadoUid: String? = null,
        denunciadoNome: String? = null,
        origem: String? = null
    ): Result<Unit>
}
