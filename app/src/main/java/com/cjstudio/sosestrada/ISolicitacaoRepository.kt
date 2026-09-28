package com.cjstudio.sosestrada

import kotlinx.coroutines.flow.Flow

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

    // Motorista envia a localização atual pro prestador (só com a
    // solicitação aceita — reforçado nas regras).
    suspend fun enviarMinhaLocalizacao(solicitacaoId: String, latitude: Double, longitude: Double): Result<Unit>

    // Total de alertas em tempo real, pra bolinha do painel e o número no
    // ícone do app. Motorista: respostas do prestador ainda não vistas +
    // mensagens não lidas. Prestador: solicitações novas + localizações do
    // motorista ainda não abertas + mensagens não lidas.
    fun escutarAlertasMotorista(): Flow<Int>
    fun escutarAlertasPrestador(): Flow<Int>

    // Zera os alertas de "resposta"/"nova" ao abrir a lista correspondente
    // (as mensagens não lidas só zeram abrindo o chat).
    suspend fun marcarRespostasComoVistas()
    suspend fun marcarNovasComoVistas()

    // Prestador abriu no mapa a localização enviada pelo motorista.
    suspend fun marcarLocalizacaoComoVista(solicitacaoId: String)

    // Exclusão permanente (as regras só permitem com status "cancelado").
    suspend fun excluir(solicitacaoId: String): Result<Unit>

    companion object {
        const val PENDENTE = "pendente"
        const val ACEITO = "aceito"
        const val RECUSADO = "recusado"
        const val CANCELADO = "cancelado"
    }
}
