package com.cjstudio.sosestrada

// Painel administrativo (app admin): login e listagens de tudo.
interface IAdminRepository {
    fun temSessao(): Boolean

    suspend fun entrar(usuario: String, senha: String): Result<Unit>

    fun sair()

    suspend fun listarMotoristas(): Result<List<Motorista>>

    suspend fun listarPrestadores(): Result<List<Prestador>>

    // Todas as solicitações, mais recentes primeiro.
    suspend fun listarSolicitacoes(): Result<List<Solicitacao>>
}
