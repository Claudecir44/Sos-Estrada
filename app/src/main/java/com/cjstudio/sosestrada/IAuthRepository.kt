package com.cjstudio.sosestrada

// Sessão do Firebase Auth (motorista e prestador usam e-mail/senha).
interface IAuthRepository {
    fun uidLogado(): String?

    fun emailLogado(): String?

    suspend fun entrar(email: String, senha: String): Result<Unit>

    // Cria a conta e já deixa a sessão aberta; devolve o uid novo.
    suspend fun criarConta(email: String, senha: String): Result<String>

    // Confirma a senha atual antes de ações sensíveis (excluir conta).
    suspend fun reautenticar(senha: String): Result<Unit>

    // Apaga a conta do Firebase Auth do usuário logado (os dados no
    // Firestore são apagados antes, pelo repositório de cada perfil).
    suspend fun excluirConta(): Result<Unit>
}
