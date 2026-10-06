package com.cjstudio.sosestrada

interface IAvaliacaoRepository {
    // Só depois que o prestador aceitou o pedido; avaliadoUid é sempre o
    // outro lado da solicitação (as regras conferem).
    suspend fun avaliar(solicitacaoId: String, avaliadoUid: String, nota: Int, comentario: String?): Result<Unit>

    // Ids das solicitações que o usuário logado já avaliou — esconde o botão
    // "Avaliar" (uma consulta só pra lista inteira).
    suspend fun solicitacoesJaAvaliadas(): Result<Set<String>>

    // Notas (média + total) de vários usuários de uma vez, pros cartões.
    suspend fun notasDe(uids: Collection<String>): Result<Map<String, NotaUsuario>>
}
