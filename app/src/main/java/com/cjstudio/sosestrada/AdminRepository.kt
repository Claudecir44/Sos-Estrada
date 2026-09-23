package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val authRepository: IAuthRepository
) : IAdminRepository {

    // Quem é admin de verdade quem decide são as regras do Firestore
    // (ehAdmin(): e-mail autorizado e validado, ou um documento em admins/).
    // Aqui é só pra barrar logo no login quem não tem acesso, com uma
    // mensagem clara em vez de listas vazias com erro de permissão.
    private fun ehEmailDeAdmin(email: String?) = email != null && email.trim().lowercase() in EMAILS_ADMIN

    override fun temSessaoDeAdmin(): Boolean {
        val usuario = auth.currentUser ?: return false
        return usuario.isEmailVerified && ehEmailDeAdmin(usuario.email)
    }

    override suspend fun entrar(email: String, senha: String): Result<Unit> = runCatching {
        // Sessão antiga (login fixo + anônimo das versões anteriores) não serve mais.
        if (auth.currentUser?.isAnonymous == true) auth.signOut()
        authRepository.entrar(email, senha).getOrThrow()
        if (!ehEmailDeAdmin(auth.currentUser?.email)) {
            auth.signOut()
            throw IllegalStateException("Esta conta não tem acesso ao painel administrativo.")
        }
        Unit
    }

    override suspend fun criarConta(email: String, senha: String): Result<Unit> = runCatching {
        if (!ehEmailDeAdmin(email)) throw IllegalArgumentException("Este e-mail não está autorizado como administrador.")
        if (auth.currentUser?.isAnonymous == true) auth.signOut()
        authRepository.criarConta(email, senha).getOrThrow()
        authRepository.enviarVerificacaoEmail()
        authRepository.sair()
        Unit
    }

    override suspend fun enviarRedefinicaoSenha(email: String): Result<Unit> = authRepository.enviarRedefinicaoSenha(email)

    override fun sair() = authRepository.sair()

    override suspend fun listarMotoristas(): Result<List<Motorista>> = runCatching {
        db.collection("motoristas").get().await().documents
            .mapNotNull { it.toObject(Motorista::class.java) }
            .sortedBy { it.nome?.lowercase() }
    }

    override suspend fun listarPrestadores(): Result<List<Prestador>> = runCatching {
        db.collection("prestadores").get().await().documents
            .mapNotNull { it.toObject(Prestador::class.java) }
            .sortedBy { it.nome?.lowercase() }
    }

    override suspend fun listarSolicitacoes(): Result<List<Solicitacao>> = runCatching {
        db.collection("solicitacoes").get().await().documents
            .mapNotNull { doc -> doc.toObject(Solicitacao::class.java)?.also { it.id = doc.id } }
            .sortedByDescending { it.timestamp?.time ?: 0L }
    }

    companion object {
        // Mesma lista de ehAdmin() em firestore.rules e do painel web
        // (web-admin/app.js) — mudar nos três lugares juntos.
        val EMAILS_ADMIN = setOf("claudecirwitkoski@gmail.com")
    }
}
