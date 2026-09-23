package com.cjstudio.sosestrada

interface IPrestadorRepository {
    // Cadastro do prestador logado; null se ainda não tem documento.
    suspend fun buscarMeuCadastro(): Result<Prestador?>

    // Cria (cadastro novo) ou atualiza prestadores/{uid} do prestador logado.
    // Na atualização só os campos do formulário mudam — os campos da
    // assinatura, gravados pela Cloud Function, ficam intactos.
    suspend fun salvarMeuCadastro(prestador: Prestador, cadastroNovo: Boolean): Result<Unit>

    suspend fun excluirMeuCadastro(): Result<Unit>

    // Prestadores visíveis na busca do motorista (ativo == true: dentro do
    // período grátis ou com assinatura em dia).
    suspend fun listarAtivos(): Result<List<Prestador>>
}
