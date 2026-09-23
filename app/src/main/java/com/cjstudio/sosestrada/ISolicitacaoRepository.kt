package com.cjstudio.sosestrada

// Pedidos de socorro (coleção solicitacoes) dos dois lados: o motorista
// pede/cancela, o prestador aceita/recusa.
interface ISolicitacaoRepository {
    // Solicitação mais recente do motorista logado com cada prestador,
    // indexada pelo uid do prestador.
    suspend fun minhasSolicitacoesPorPrestador(): Result<Map<String, Solicitacao>>

    // Se o motorista logado já tem solicitação pendente ou aceita com o prestador.
    suspend fun temSolicitacaoAtivaCom(prestadorUid: String): Result<Boolean>

    // Cria a solicitação com os dados do cadastro do motorista logado;
    // devolve o id do documento novo.
    suspend fun solicitar(prestador: Prestador, latitude: Double, longitude: Double, endereco: String?): Result<String>

    // Solicitações recebidas pelo prestador logado, mais recentes primeiro.
    suspend fun recebidasPeloPrestador(): Result<List<Solicitacao>>

    suspend fun atualizarStatus(solicitacaoId: String, status: String): Result<Unit>

    // Prestador aceita: muda o status e avisa o motorista pelo chat.
    suspend fun aceitar(solicitacaoId: String): Result<Unit>

    // Exclusão permanente (as regras só permitem com status "cancelado").
    suspend fun excluir(solicitacaoId: String): Result<Unit>

    companion object {
        const val PENDENTE = "pendente"
        const val ACEITO = "aceito"
        const val RECUSADO = "recusado"
        const val CANCELADO = "cancelado"
    }
}
