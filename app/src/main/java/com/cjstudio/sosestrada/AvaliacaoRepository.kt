package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvaliacaoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IAvaliacaoRepository {

    override suspend fun avaliar(solicitacaoId: String, avaliadoUid: String, nota: Int, comentario: String?): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
        require(nota in 1..5) { "Escolha de 1 a 5 estrelas." }
        val avaliacao = Avaliacao(
            solicitacaoId = solicitacaoId,
            avaliadorUid = uid,
            avaliadoUid = avaliadoUid,
            nota = nota,
            comentario = comentario?.trim()?.take(LIMITE_COMENTARIO)?.ifEmpty { null }
        )
        db.collection(COLECAO).document("${solicitacaoId}_$uid").set(avaliacao).await()
        Unit
    }

    override suspend fun solicitacoesJaAvaliadas(): Result<Set<String>> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching emptySet()
        db.collection(COLECAO).whereEqualTo("avaliadorUid", uid).get().await()
            .documents.mapNotNull { it.getString("solicitacaoId") }.toSet()
    }

    // whereIn aceita até 30 ids por consulta.
    override suspend fun notasDe(uids: Collection<String>): Result<Map<String, NotaUsuario>> = runCatching {
        uids.filter { it.isNotBlank() }.distinct().chunked(30).flatMap { lote ->
            db.collection(COLECAO_NOTAS).whereIn(FieldPath.documentId(), lote).get().await().documents.map { doc ->
                doc.id to NotaUsuario(doc.getDouble("media") ?: 0.0, (doc.getLong("total") ?: 0L).toInt())
            }
        }.toMap()
    }

    companion object {
        const val COLECAO = "avaliacoes"
        const val COLECAO_NOTAS = "notasUsuarios"
        const val LIMITE_COMENTARIO = 500
    }
}
