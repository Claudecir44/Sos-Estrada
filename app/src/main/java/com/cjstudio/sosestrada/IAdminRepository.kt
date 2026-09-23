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

    // Cadastro (admins/{uid}) do admin logado — nome pra saudação do painel.
    suspend fun buscarMeuCadastro(): Result<Admin?>

    // "Meu Perfil": atualiza nome, sobrenome, telefone, CPF e foto do admin
    // logado (e-mail e senha de login não mudam por aqui).
    suspend fun atualizarMeuCadastro(admin: Admin): Result<Unit>

    // "Meu Perfil" > Excluir: confirma a senha, remove o acesso de admin e,
    // se a conta não for também de motorista/prestador, apaga o login.
    suspend fun excluirMeuCadastro(senha: String): Result<Unit>

    suspend fun listarMotoristas(): Result<List<Motorista>>

    suspend fun listarPrestadores(): Result<List<Prestador>>

    // Todas as solicitações, mais recentes primeiro.
    suspend fun listarSolicitacoes(): Result<List<Solicitacao>>

    // Ações do admin sobre o cadastro de outra pessoa (segurar o cartão nas
    // listas). Todas exigem a senha do administrador master — conferida
    // pelas regras do Firestore; errada, falham com SenhaMasterIncorretaException.
    suspend fun editarMotorista(uid: String, dados: Map<String, Any?>, senhaMaster: String): Result<Unit>

    suspend fun editarPrestador(uid: String, dados: Map<String, Any?>, senhaMaster: String): Result<Unit>

    suspend fun definirBloqueio(colecao: String, uid: String, bloquear: Boolean, senhaMaster: String): Result<Unit>

    // Apaga o cadastro (o login no Firebase Auth fica — apagar a conta de
    // outra pessoa exige Cloud Function —, mas sem cadastro não entra no app).
    suspend fun excluirCadastro(colecao: String, uid: String, senhaMaster: String): Result<Unit>
}

class SenhaMasterIncorretaException : Exception("Senha do administrador master incorreta.")
