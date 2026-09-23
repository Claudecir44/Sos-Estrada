package com.cjstudio.sosestrada

interface IMotoristaRepository {
    // Cadastro do motorista logado; null se ainda não tem documento.
    suspend fun buscarMeuCadastro(): Result<Motorista?>

    // Cria ou substitui motoristas/{uid} do motorista logado.
    suspend fun salvarMeuCadastro(motorista: Motorista): Result<Unit>

    suspend fun excluirMeuCadastro(): Result<Unit>
}
