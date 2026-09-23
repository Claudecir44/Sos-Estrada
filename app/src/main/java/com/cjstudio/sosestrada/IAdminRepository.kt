package com.cjstudio.sosestrada

// Painel administrativo (app admin e painel web): cadastro e login por
// e-mail e senha, e listagens de tudo.
interface IAdminRepository {
    // Sessão aberta de um admin com e-mail validado e cadastro em admins/.
    suspend fun temSessaoDeAdmin(): Boolean

    // Entra, exige e-mail validado e confere se a conta tem cadastro de admin.
    suspend fun entrar(email: String, senha: String): Result<Unit>

    // Cria a conta + admins/{uid}, autorizado pela senha do administrador
    // master, e manda a verificação de e-mail. Senha master errada desfaz a
    // conta recém-criada e falha com SenhaMasterIncorretaException.
    suspend fun cadastrarAdmin(admin: Admin, senha: String, senhaMaster: String): Result<Unit>

    suspend fun enviarRedefinicaoSenha(email: String): Result<Unit>

    fun sair()

    fun emailLogado(): String?

    suspend fun listarMotoristas(): Result<List<Motorista>>

    suspend fun listarPrestadores(): Result<List<Prestador>>

    // Todas as solicitações, mais recentes primeiro.
    suspend fun listarSolicitacoes(): Result<List<Solicitacao>>
}

class SenhaMasterIncorretaException : Exception("Senha do administrador master incorreta.")
