package com.cjstudio.sosestrada

import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.ACEITO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.CANCELADO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.PENDENTE
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.RECUSADO
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolicitacaoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val motoristaRepository: IMotoristaRepository,
    private val chatRepository: IChatRepository
) : ISolicitacaoRepository {

    private fun uidLogado(): String = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

    private fun colecao() = db.collection("solicitacoes")

    private fun DocumentSnapshot.paraSolicitacao(): Solicitacao? =
        toObject(Solicitacao::class.java)?.also { it.id = id }

    // Com mais de uma solicitação pro mesmo prestador, vale a mais recente.
    private fun maisRecentePorPrestador(documentos: List<DocumentSnapshot>): Map<String, Solicitacao> =
        documentos.mapNotNull { it.paraSolicitacao() }
            .filter { it.prestadorUid != null }
            .groupBy { it.prestadorUid!! }
            .mapValues { (_, solicitacoes) -> solicitacoes.maxBy { it.timestamp?.time ?: 0L } }

    override suspend fun minhasSolicitacoesPorPrestador(): Result<Map<String, Solicitacao>> = runCatching {
        maisRecentePorPrestador(colecao().whereEqualTo("motoristaUid", uidLogado()).get().await().documents)
    }

    override fun escutarMinhasSolicitacoesPorPrestador(): Flow<Map<String, Solicitacao>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val registro = colecao().whereEqualTo("motoristaUid", uid).addSnapshotListener { snapshots, erro ->
            if (erro != null || snapshots == null) return@addSnapshotListener
            trySend(maisRecentePorPrestador(snapshots.documents))
        }
        awaitClose { registro.remove() }
    }

    override fun escutarRecebidasPeloPrestador(): Flow<List<Solicitacao>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val registro = colecao().whereEqualTo("prestadorUid", uid).addSnapshotListener { snapshots, erro ->
            if (erro != null || snapshots == null) return@addSnapshotListener
            trySend(snapshots.documents.mapNotNull { it.paraSolicitacao() }.sortedByDescending { it.timestamp?.time ?: 0L })
        }
        awaitClose { registro.remove() }
    }

    override suspend fun temSolicitacaoAtivaCom(prestadorUid: String): Result<Boolean> = runCatching {
        !colecao()
            .whereEqualTo("motoristaUid", uidLogado())
            .whereEqualTo("prestadorUid", prestadorUid)
            .whereIn("status", listOf(PENDENTE, ACEITO))
            .get().await().isEmpty
    }

    override suspend fun solicitar(
        prestador: Prestador,
        latitude: Double,
        longitude: Double,
        endereco: String?
    ): Result<String> = runCatching {
        val motorista = motoristaRepository.buscarMeuCadastro().getOrThrow()
            ?: throw IllegalStateException("Dados do motorista não encontrados.")
        val dados = hashMapOf<String, Any?>(
            "motoristaUid" to uidLogado(),
            "prestadorUid" to prestador.uid,
            "prestadorNome" to prestador.nome,
            "motoristaNome" to motorista.nome,
            "motoristaTelefone" to motorista.telefone,
            "motoristaVeiculo" to motorista.veiculo,
            "motoristaPlaca" to motorista.placa,
            "motoristaSexo" to motorista.sexo,
            "status" to PENDENTE,
            "timestamp" to Date(),
            "latitudeMotorista" to latitude,
            "longitudeMotorista" to longitude,
            "novaParaPrestador" to true
        )
        if (endereco != null) dados["enderecoMotorista"] = endereco
        colecao().add(dados).await().id
    }

    override suspend fun recebidasPeloPrestador(): Result<List<Solicitacao>> = runCatching {
        colecao().whereEqualTo("prestadorUid", uidLogado()).get().await().documents
            .mapNotNull { it.paraSolicitacao() }
            .sortedByDescending { it.timestamp?.time ?: 0L }
    }

    override suspend fun atualizarStatus(solicitacaoId: String, status: String): Result<Unit> = runCatching {
        val mudancas = mutableMapOf<String, Any>("status" to status)
        // Resposta do prestador: acende o alerta do motorista (bolinha em
        // "Preciso de socorro" + push "Prestador respondeu").
        if (status == ACEITO || status == RECUSADO) mudancas["respostaNaoVistaMotorista"] = true
        colecao().document(solicitacaoId).update(mudancas).await()
        Unit
    }

    // Motorista: só a solicitação mais recente com cada prestador (é a única
    // que aparece na lista do socorro, então é a única cujo chat dá pra abrir
    // e zerar — contar as antigas deixaria a bolinha presa pra sempre).
    override fun escutarAlertasMotorista(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { trySend(0); close(); return@callbackFlow }
        val registro = colecao().whereEqualTo("motoristaUid", uid).addSnapshotListener { snapshots, erro ->
            if (erro != null || snapshots == null) return@addSnapshotListener
            val total = snapshots.documents.mapNotNull { it.paraSolicitacao() }
                .filter { it.prestadorUid != null }
                .groupBy { it.prestadorUid!! }
                .values.map { lista -> lista.maxBy { it.timestamp?.time ?: 0L } }
                .sumOf { (if (it.respostaNaoVistaMotorista) 1 else 0) + it.naoLidasMotorista }
            trySend(total)
        }
        awaitClose { registro.remove() }
    }

    // Prestador: solicitação cancelada não mostra o botão Mensagem, então
    // suas mensagens não lidas não entram na conta.
    override fun escutarAlertasPrestador(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { trySend(0); close(); return@callbackFlow }
        val registro = colecao().whereEqualTo("prestadorUid", uid).addSnapshotListener { snapshots, erro ->
            if (erro != null || snapshots == null) return@addSnapshotListener
            val total = snapshots.documents.mapNotNull { it.paraSolicitacao() }
                .filter { it.status != CANCELADO }
                .sumOf {
                    (if (it.novaParaPrestador) 1 else 0) +
                        (if (it.localizacaoNaoVistaPrestador && it.latitudeCompartilhada != null) 1 else 0) +
                        it.naoLidasPrestador
                }
            trySend(total)
        }
        awaitClose { registro.remove() }
    }

    override suspend fun removerMinhaLocalizacao(solicitacaoId: String): Result<Unit> = runCatching {
        colecao().document(solicitacaoId).update(
            mapOf(
                "latitudeCompartilhada" to FieldValue.delete(),
                "longitudeCompartilhada" to FieldValue.delete(),
                "localizacaoCompartilhadaEm" to FieldValue.delete(),
                "localizacaoNaoVistaPrestador" to false
            )
        ).await()
        Unit
    }

    override suspend fun marcarLocalizacaoComoVista(solicitacaoId: String) {
        runCatching { colecao().document(solicitacaoId).update("localizacaoNaoVistaPrestador", false).await() }
    }

    override suspend fun marcarRespostasComoVistas() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            colecao().whereEqualTo("motoristaUid", uid).get().await().documents
                .filter { it.getBoolean("respostaNaoVistaMotorista") == true }
                .forEach { it.reference.update("respostaNaoVistaMotorista", false).await() }
        }
    }

    override suspend fun marcarNovasComoVistas() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            colecao().whereEqualTo("prestadorUid", uid).get().await().documents
                .filter { it.getBoolean("novaParaPrestador") == true }
                .forEach { it.reference.update("novaParaPrestador", false).await() }
        }
    }

    override suspend fun aceitar(solicitacaoId: String): Result<Unit> = runCatching {
        atualizarStatus(solicitacaoId, ACEITO).getOrThrow()
        // O diálogo de aceite promete esse aviso ao motorista. Se o chat
        // falhar, o aceite já valeu — não desfaz por causa da mensagem.
        chatRepository.enviarMensagem(
            solicitacaoId,
            IChatRepository.PRESTADOR,
            texto = "✅ Aceitei sua solicitação e estou a caminho para o socorro.\n📍 Envie sua localização para um atendimento melhor!",
            imagemUrl = null,
            automatica = true
        )
        Unit
    }

    override suspend fun enviarMinhaLocalizacao(
        solicitacaoId: String,
        latitude: Double,
        longitude: Double
    ): Result<Unit> = runCatching {
        colecao().document(solicitacaoId).update(
            mapOf(
                "latitudeCompartilhada" to latitude,
                "longitudeCompartilhada" to longitude,
                "localizacaoCompartilhadaEm" to FieldValue.serverTimestamp(),
                // Acende a bolinha do botão no card do prestador (e o push
                // "Localização Sos Estrada" — functions/index.js).
                "localizacaoNaoVistaPrestador" to true
            )
        ).await()
        Unit
    }

    override suspend fun excluir(solicitacaoId: String): Result<Unit> = runCatching {
        colecao().document(solicitacaoId).delete().await()
        Unit
    }
}
