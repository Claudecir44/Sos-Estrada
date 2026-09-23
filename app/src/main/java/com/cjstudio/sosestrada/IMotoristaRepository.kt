package com.cjstudio.sosestrada

interface IMotoristaRepository {
    // Cadastro do motorista logado; null se ainda não tem documento.
    suspend fun buscarMeuCadastro(): Result<Motorista?>

    // Cria (cadastro novo) ou atualiza motoristas/{uid} do motorista logado.
    // Na atualização, sem foto nova fica a que já estava.
    suspend fun salvarMeuCadastro(motorista: Motorista, cadastroNovo: Boolean): Result<Unit>

    // Depois que o motorista confirma um e-mail novo pelo link, o login já
    // é o novo mas o cadastro ainda guarda o antigo: acerta o cadastro.
    suspend fun sincronizarEmail(emailDoLogin: String)

    suspend fun excluirMeuCadastro(): Result<Unit>
}
