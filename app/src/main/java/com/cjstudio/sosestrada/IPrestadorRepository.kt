package com.cjstudio.sosestrada

interface IPrestadorRepository {
    // Cadastro do prestador logado; null se ainda não tem documento.
    suspend fun buscarMeuCadastro(): Result<Prestador?>

    // Cria (cadastro novo) ou atualiza prestadores/{uid} do prestador logado.
    // Na atualização só os campos do formulário mudam — os campos da
    // assinatura, gravados pela Cloud Function, ficam intactos.
    suspend fun salvarMeuCadastro(prestador: Prestador, cadastroNovo: Boolean): Result<Unit>

    suspend fun excluirMeuCadastro(): Result<Unit>

    // Cadastro novo, logo depois de criar a conta: "livre", "sem_trial" (o
    // CPF/CNPJ já usou o período grátis) ou "em_uso" (outro cadastro de
    // prestador ativo tem esse documento).
    suspend fun verificarDocumento(documento: String): Result<String>

    // Prestadores visíveis na busca do motorista (ativo == true: dentro do
    // período grátis ou com assinatura em dia).
    suspend fun listarAtivos(): Result<List<Prestador>>
}
