package com.cjstudio.sosestrada

// Painel administrativo (app admin e painel web): login por e-mail e senha
// e listagens de tudo.
interface IAdminRepository {
    // Sessão aberta de um admin com e-mail já validado.
    fun temSessaoDeAdmin(): Boolean

    // Entra, exige e-mail validado e confere se a conta é de admin.
    suspend fun entrar(email: String, senha: String): Result<Unit>

    // Cria a conta de admin (só o e-mail autorizado) e manda a verificação.
    suspend fun criarConta(email: String, senha: String): Result<Unit>

    suspend fun enviarRedefinicaoSenha(email: String): Result<Unit>

    fun sair()

    suspend fun listarMotoristas(): Result<List<Motorista>>

    suspend fun listarPrestadores(): Result<List<Prestador>>

    // Todas as solicitações, mais recentes primeiro.
    suspend fun listarSolicitacoes(): Result<List<Solicitacao>>
}
