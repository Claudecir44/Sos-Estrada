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

    // Suporte da tela inicial ("Reenviar e-mail de validação"): entra com
    // e-mail e senha só pra pedir o reenvio e sai em seguida. Devolve a
    // mensagem pra mostrar (reenviado, já validado, aguarde, etc.).
    suspend fun reenviarValidacao(email: String, senha: String): Result<String>

    // Troca o e-mail de login: confirma a senha atual e manda um link pro
    // e-mail NOVO — o login só muda depois que a pessoa abre esse link.
    suspend fun trocarEmail(novoEmail: String, senha: String): Result<Unit>

    // Confirma a senha atual antes de ações sensíveis (excluir conta).
    suspend fun reautenticar(senha: String): Result<Unit>

    // Apaga a conta do Firebase Auth do usuário logado (os dados no
    // Firestore são apagados antes, pelo repositório de cada perfil).
    suspend fun excluirConta(): Result<Unit>

    fun sair()

    // Perfis com cadastro na conta logada (IChatRepository.MOTORISTA /
    // PRESTADOR). Cada e-mail é de um perfil só: o login de motorista
    // recusa conta de prestador e vice-versa.
    suspend fun perfisDaConta(): Set<String>

    // Conta bloqueada pelo admin (bloqueados/{uid}) — vale mesmo depois de o
    // admin excluir o cadastro, pra pessoa não se recadastrar.
    suspend fun contaBloqueada(): Boolean
}

class EmailNaoVerificadoException(mensagem: String) : Exception(mensagem)
