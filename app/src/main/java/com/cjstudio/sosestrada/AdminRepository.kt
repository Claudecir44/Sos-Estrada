package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IAdminRepository {

    override fun temSessao(): Boolean = auth.currentUser != null

    // ============================================================
    // TEMPORÁRIO (decisão do usuário em 2026-08-15): o projeto está no plano
    // Spark, então a Cloud Function provisionarAdminInicial (conta real de
    // admin por e-mail/senha + coleção admins/) não pode rodar. Até lá o
    // login compara usuário/senha fixos aqui e abre uma sessão anônima — que
    // nas regras do Firestore é indistinguível de qualquer usuário anônimo
    // (ver ehAdminTemporario() em firestore.rules). Trocar por
    // auth.signInWithEmailAndPassword assim que o Blaze for ativado, e
    // remover o bypass das regras junto.
    // ============================================================
    override suspend fun entrar(usuario: String, senha: String): Result<Unit> = runCatching {
        if (usuario != ADMIN_USER || senha != ADMIN_PASSWORD) {
            throw IllegalArgumentException("Usuário ou senha incorretos.")
        }
        if (auth.currentUser == null) auth.signInAnonymously().await()
        Unit
    }

    override fun sair() = auth.signOut()

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
        private const val ADMIN_USER = "Programador"
        private const val ADMIN_PASSWORD = "SENHA_REMOVIDA"
    }
}
