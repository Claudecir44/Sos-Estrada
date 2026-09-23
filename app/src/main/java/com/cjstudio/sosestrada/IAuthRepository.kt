package com.cjstudio.sosestrada

// Sessão do Firebase Auth por e-mail e senha (motorista, prestador e admin).
// Toda conta precisa validar o e-mail antes de conseguir entrar.
interface IAuthRepository {
    fun uidLogado(): String?

    fun emailLogado(): String?

    // Entra e confere se o e-mail já foi validado. Se não foi, reenvia a
    // verificação, encerra a sessão e falha com EmailNaoVerificadoException.
    suspend fun entrar(email: String, senha: String): Result<Unit>

    // Cria a conta e já deixa a sessão aberta (pra gravar o cadastro no
    // Firestore em seguida); devolve o uid novo.
    suspend fun criarConta(email: String, senha: String): Result<String>

    // Manda o e-mail de verificação pra conta logada. Falha não desfaz nada:
    // o login reenvia se a pessoa tentar entrar sem ter validado.
    suspend fun enviarVerificacaoEmail(): Result<Unit>

    suspend fun enviarRedefinicaoSenha(email: String): Result<Unit>

    // Confirma a senha atual antes de ações sensíveis (excluir conta).
    suspend fun reautenticar(senha: String): Result<Unit>

    // Apaga a conta do Firebase Auth do usuário logado (os dados no
    // Firestore são apagados antes, pelo repositório de cada perfil).
    suspend fun excluirConta(): Result<Unit>

    fun sair()
}

class EmailNaoVerificadoException(mensagem: String) : Exception(mensagem)
