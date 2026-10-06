package com.cjstudio.sosestrada

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatAdminRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) : IChatAdminRepository {

    private fun conversas() = db.collection(COLECAO_CONVERSAS)
    private fun mensagens(conversaId: String) = conversas().document(conversaId).collection("mensagens")
    private fun uidObrigatorio() = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

    // Id determinístico (par de uids em ordem): get() direto por id, que as
    // regras sempre conseguem validar — mesmo esquema do Caronas e do painel web.
    private fun idConversaEntre(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    private fun nomeCompleto(nome: String?, sobrenome: String?, email: String?) =
        listOfNotNull(nome, sobrenome).joinToString(" ").trim().ifBlank { email.orEmpty() }.ifBlank { "Administrador" }

    private suspend fun meuNome(uid: String): String {
        val doc = db.collection("admins").document(uid).get().await()
        return nomeCompleto(doc.getString("nome"), doc.getString("sobrenome"), doc.getString("email"))
    }

    override fun meuUid(): String? = auth.currentUser?.uid

    override fun escutarMinhasConversas(): Flow<List<ConversaAdmin>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val registro = conversas()
            .whereArrayContains("participantes", uid)
            .orderBy("ultimoTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, erro ->
                if (erro != null) { close(erro); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { d -> d.toObject(ConversaAdmin::class.java)?.apply { id = d.id } }.orEmpty())
            }
        awaitClose { registro.remove() }
    }

    override suspend fun listarContatos(): Result<List<ContatoAdmin>> = runCatching {
        val uid = uidObrigatorio()
        db.collection("admins").get().await().documents
            .filter { it.id != uid }
            .map { d ->
                ContatoAdmin(
                    id = d.id,
                    nome = nomeCompleto(d.getString("nome"), d.getString("sobrenome"), d.getString("email")),
                    email = d.getString("email"),
                    foto = d.getString("foto"),
                    colaborador = d.getString("role") == Admin.ROLE_COLABORADOR
                )
            }
            .sortedBy { it.nome.lowercase() }
    }

    override suspend fun buscarOuCriarConversa(contato: ContatoAdmin): Result<ConversaAdmin> = runCatching {
        val uid = uidObrigatorio()
        val ref = conversas().document(idConversaEntre(uid, contato.id))
        val existente = ref.get().await()
        if (existente.exists()) return@runCatching existente.toObject(ConversaAdmin::class.java)!!.apply { id = existente.id }

        val eu = meuNome(uid)
        val souAdmin1 = listOf(uid, contato.id).sorted().first() == uid
        val nova = ConversaAdmin(
            participantes = listOf(uid, contato.id),
            admin1Id = if (souAdmin1) uid else contato.id,
            admin1Nome = if (souAdmin1) eu else contato.nome,
            admin2Id = if (souAdmin1) contato.id else uid,
            admin2Nome = if (souAdmin1) contato.nome else eu,
            ultimaMensagem = ""
        )
        ref.set(nova).await()
        nova.apply { id = ref.id }
    }

    override fun escutarMensagens(conversaId: String): Flow<List<MensagemChatAdmin>> = callbackFlow {
        val registro = mensagens(conversaId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, erro ->
                if (erro != null) { close(erro); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { d ->
                    d.toObject(MensagemChatAdmin::class.java)?.apply {
                        id = d.id
                        this.conversaId = conversaId
                        conteudo = ChatAdminCryptoUtil.descriptografar(conteudo)
                    }
                }.orEmpty())
            }
        awaitClose { registro.remove() }
    }

    override suspend fun enviarMensagem(conversa: ConversaAdmin, texto: String): Result<Unit> = runCatching {
        val uid = uidObrigatorio()
        val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
        val destinatario = conversa.idOutroAdmin(uid) ?: throw IllegalStateException("Conversa sem o outro participante.")
        mensagens(conversaId).add(
            MensagemChatAdmin(
                remetenteId = uid,
                remetenteNome = meuNome(uid),
                destinatarioId = destinatario,
                destinatarioNome = conversa.nomeOutroAdmin(uid),
                conteudo = ChatAdminCryptoUtil.criptografar(texto)
            )
        ).await()
        // Soma 1 nas não lidas de quem RECEBE. A prévia na lista fica em texto
        // (igual ao Caronas); o conteúdo das mensagens é que vai cifrado.
        val campoNaoLidas = if (conversa.admin1Id == uid) "naoLidas2" else "naoLidas1"
        conversas().document(conversaId).update(
            mapOf(
                "ultimaMensagem" to texto.take(120),
                "ultimoTimestamp" to FieldValue.serverTimestamp(),
                "ultimoRemetenteId" to uid,
                campoNaoLidas to FieldValue.increment(1)
            )
        ).await()
        Unit
    }

    override suspend fun marcarConversaComoLida(conversa: ConversaAdmin): Result<Unit> = runCatching {
        val uid = uidObrigatorio()
        val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
        conversas().document(conversaId).update(if (conversa.admin1Id == uid) "naoLidas1" else "naoLidas2", 0).await()
        val naoLidas = mensagens(conversaId).whereEqualTo("destinatarioId", uid).whereEqualTo("lida", false).get().await()
        if (!naoLidas.isEmpty) {
            val lote = db.batch()
            naoLidas.documents.forEach { lote.update(it.reference, "lida", true) }
            lote.commit().await()
        }
        Unit
    }

    override suspend fun apagarMensagemParaMim(mensagem: MensagemChatAdmin): Result<Unit> = runCatching {
        val uid = uidObrigatorio()
        val campo = if (mensagem.remetenteId == uid) "deletadoParaRemetente" else "deletadoParaDestinatario"
        mensagens(mensagem.conversaId!!).document(mensagem.id!!).update(campo, true).await()
        Unit
    }

    override suspend fun apagarMensagemParaTodos(mensagem: MensagemChatAdmin): Result<Unit> = runCatching {
        mensagens(mensagem.conversaId!!).document(mensagem.id!!).update(
            mapOf(
                "deletadoParaTodos" to true,
                "conteudo" to ChatAdminCryptoUtil.criptografar(MENSAGEM_APAGADA),
                "tipo" to "deletada"
            )
        ).await()
        Unit
    }

    override suspend fun apagarConversa(conversa: ConversaAdmin): Result<Unit> = runCatching {
        val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
        // Lote tem limite de 500 operações.
        mensagens(conversaId).get().await().documents.chunked(400).forEach { bloco ->
            val lote = db.batch()
            bloco.forEach { lote.delete(it.reference) }
            lote.commit().await()
        }
        conversas().document(conversaId).delete().await()
        Unit
    }

    override fun escutarTotalNaoLidas(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(0); close(); return@callbackFlow
        }
        val registro = conversas().whereArrayContains("participantes", uid)
            .addSnapshotListener { snap, erro ->
                if (erro != null) { close(erro); return@addSnapshotListener }
                trySend(snap?.documents?.sumOf { d -> d.toObject(ConversaAdmin::class.java)?.naoLidasParaMim(uid) ?: 0 } ?: 0)
            }
        awaitClose { registro.remove() }
    }

    override fun registrarTokenAdmin() {
        val uid = auth.currentUser?.uid ?: return
        messaging.token
            .addOnSuccessListener { token ->
                db.collection("admins").document(uid).update("fcmToken", token)
                    .addOnFailureListener { e -> Log.e(TAG, "Erro ao salvar token do admin: ${e.message}") }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Erro ao obter token: ${e.message}") }
    }

    companion object {
        const val COLECAO_CONVERSAS = "conversasAdmin"
        const val MENSAGEM_APAGADA = "Mensagem apagada"
        private const val TAG = "ChatAdminRepository"
    }
}
