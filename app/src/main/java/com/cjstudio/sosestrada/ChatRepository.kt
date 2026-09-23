package com.cjstudio.sosestrada

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : IChatRepository {

    private fun solicitacao(solicitacaoId: String) = db.collection("solicitacoes").document(solicitacaoId)

    private fun mensagens(solicitacaoId: String) = solicitacao(solicitacaoId).collection("mensagens")

    // Contador agregado em solicitacoes/{id}: as listas mostram o badge sem
    // precisar contar mensagens "lida:false" de cada item.
    private fun campoNaoLidasDe(tipo: String) =
        if (tipo == IChatRepository.MOTORISTA) "naoLidasMotorista" else "naoLidasPrestador"

    private fun outroTipo(meuTipo: String) =
        if (meuTipo == IChatRepository.MOTORISTA) IChatRepository.PRESTADOR else IChatRepository.MOTORISTA

    override fun escutarMensagens(solicitacaoId: String): Flow<List<Mensagem>> = callbackFlow {
        val registro = mensagens(solicitacaoId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, erro ->
                if (erro != null || snapshots == null) return@addSnapshotListener
                trySend(snapshots.documents.mapNotNull { doc ->
                    doc.toObject(Mensagem::class.java)?.also { it.id = doc.id }
                })
            }
        awaitClose { registro.remove() }
    }

    override suspend fun enviarMensagem(
        solicitacaoId: String,
        meuTipo: String,
        texto: String?,
        imagemUrl: String?
    ): Result<Unit> = runCatching {
        val dados = hashMapOf(
            "remetenteUid" to auth.currentUser?.uid,
            "remetenteTipo" to meuTipo,
            "texto" to texto,
            "imagemUrl" to imagemUrl,
            "timestamp" to Date(),
            "lida" to false
        )
        mensagens(solicitacaoId).add(dados).await()
        solicitacao(solicitacaoId).update(campoNaoLidasDe(outroTipo(meuTipo)), FieldValue.increment(1)).await()
        Unit
    }

    override suspend fun enviarImagem(solicitacaoId: String, imagem: Uri): Result<String> = runCatching {
        val referencia = storage.reference.child("mensagens_imagens/$solicitacaoId/${UUID.randomUUID()}.jpg")
        referencia.putFile(imagem).await()
        referencia.downloadUrl.await().toString()
    }

    override suspend fun marcarComoLidas(solicitacaoId: String, mensagens: List<Mensagem>, meuTipo: String) {
        mensagens
            .filter { it.remetenteTipo != null && it.remetenteTipo != meuTipo && !it.lida && it.id != null }
            .forEach { mensagem ->
                runCatching { mensagens(solicitacaoId).document(mensagem.id!!).update("lida", true).await() }
            }
    }

    override suspend fun zerarNaoLidas(solicitacaoId: String, meuTipo: String) {
        runCatching { solicitacao(solicitacaoId).update(campoNaoLidasDe(meuTipo), 0).await() }
    }

    override suspend fun limparMensagensAntigas(solicitacaoId: String) {
        val limite = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.time
        val antigas = runCatching {
            mensagens(solicitacaoId).whereLessThan("timestamp", limite).get().await().documents
        }.getOrNull() ?: return
        for (doc in antigas) {
            doc.getString("imagemUrl")?.takeIf { it.isNotEmpty() }?.let { url ->
                // A foto pode já ter sido removida — não impede apagar a mensagem.
                runCatching { storage.getReferenceFromUrl(url).delete().await() }
            }
            runCatching { doc.reference.delete().await() }
        }
    }
}
