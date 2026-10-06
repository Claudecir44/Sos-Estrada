package com.cjstudio.sosestrada

// Aceite dos Termos de uso e privacidade, separado por PERFIL: a mesma conta
// (mesmo e-mail) pode ser admin e motorista, por exemplo, e cada perfil
// aceita por conta própria. O aceite fica no documento do perfil
// (motoristas/{uid}, prestadores/{uid} ou admins/{uid} — admin e colaborador).
interface ITermosRepository {

    // true se o perfil já aceitou a versão vigente (VERSAO_TERMOS).
    suspend fun aceitou(perfil: String): Result<Boolean>

    suspend fun registrarAceite(perfil: String): Result<Unit>

    companion object {
        const val PERFIL_MOTORISTA = "motoristas"
        const val PERFIL_PRESTADOR = "prestadores"
        const val PERFIL_ADMIN = "admins"

        // Subir este número quando os termos mudarem: todo mundo aceita de novo.
        const val VERSAO_TERMOS = "1"

        const val CAMPO_VERSAO = "termosVersao"
        const val CAMPO_ACEITO_EM = "termosAceitosEm"
    }
}
