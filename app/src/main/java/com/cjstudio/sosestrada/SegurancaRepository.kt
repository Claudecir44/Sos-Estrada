package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegurancaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ISegurancaRepository {

    private fun meuUid() = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

    // ID "{bloqueador}_{bloqueado}": as regras checam com exists(), sem consulta.
    private fun bloqueio(de: String, para: String) = db.collection(COLECAO_BLOQUEIOS).document("${de}_$para")

    override suspend fun bloquear(outroUid: String, outroNome: String?): Result<Unit> = runCatching {
        val uid = meuUid()
        require(outroUid != uid) { "Você não pode bloquear a si mesmo." }
        bloqueio(uid, outroUid).set(
            mapOf(
                "bloqueadorUid" to uid,
                "bloqueadoUid" to outroUid,
                "bloqueadoNome" to outroNome?.trim()?.take(120),
                "criadoEm" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    override suspend fun desbloquear(outroUid: String): Result<Unit> = runCatching {
        bloqueio(meuUid(), outroUid).delete().await()
        Unit
    }

    override suspend fun bloqueei(outroUid: String): Result<Boolean> = runCatching {
        bloqueio(meuUid(), outroUid).get().await().exists()
    }

    override suspend fun meusBloqueios(): Result<Map<String, String>> = runCatching {
        db.collection(COLECAO_BLOQUEIOS).whereEqualTo("bloqueadorUid", meuUid()).get().await().documents
            .mapNotNull { doc ->
                val uid = doc.getString("bloqueadoUid") ?: return@mapNotNull null
                uid to (doc.getString("bloqueadoNome") ?: "Usuário")
            }.toMap()
    }

    override suspend fun idsComBloqueio(): Result<Set<String>> = runCatching {
        val uid = meuUid()
        val bloqueei = db.collection(COLECAO_BLOQUEIOS).whereEqualTo("bloqueadorUid", uid).get().await()
            .documents.mapNotNull { it.getString("bloqueadoUid") }
        val meBloquearam = db.collection(COLECAO_BLOQUEIOS).whereEqualTo("bloqueadoUid", uid).get().await()
            .documents.mapNotNull { it.getString("bloqueadorUid") }
        (bloqueei + meBloquearam).toSet()
    }

    override suspend fun enviarManifestacao(
        tipo: String,
        mensagem: String,
        motivo: String?,
        denunciadoUid: String?,
        denunciadoNome: String?,
        origem: String?
    ): Result<Unit> = runCatching {
        val uid = meuUid()
        val texto = mensagem.trim()
        require(texto.isNotEmpty() || tipo == Manifestacao.DENUNCIA) { "Escreva a mensagem." }
        // Nome do próprio cadastro (motorista ou prestador), pro admin saber quem é.
        val nome = listOf("motoristas", "prestadores").firstNotNullOfOrNull { colecao ->
            runCatching { db.collection(colecao).document(uid).get().await().getString("nome") }.getOrNull()
        }
        val dados = mutableMapOf<String, Any?>(
            "uid" to uid,
            "nome" to nome,
            "email" to auth.currentUser?.email,
            "tipo" to tipo,
            "mensagem" to texto.take(LIMITE_MENSAGEM),
            "status" to Manifestacao.STATUS_NOVA,
            "criadoEm" to FieldValue.serverTimestamp()
        )
        if (tipo == Manifestacao.DENUNCIA) {
            dados["motivo"] = motivo
            dados["denunciadoUid"] = denunciadoUid
            dados["denunciadoNome"] = denunciadoNome?.take(120)
            dados["origem"] = origem
        }
        db.collection(COLECAO_MANIFESTACOES).add(dados).await()
        Unit
    }

    companion object {
        const val COLECAO_BLOQUEIOS = "bloqueios"
        const val COLECAO_MANIFESTACOES = "manifestacoes"
        const val LIMITE_MENSAGEM = 2000
    }
}
