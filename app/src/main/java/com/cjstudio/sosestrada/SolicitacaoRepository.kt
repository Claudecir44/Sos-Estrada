package com.cjstudio.sosestrada

import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.ACEITO
import com.cjstudio.sosestrada.ISolicitacaoRepository.Companion.PENDENTE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

    override suspend fun minhasSolicitacoesPorPrestador(): Result<Map<String, Solicitacao>> = runCatching {
        colecao().whereEqualTo("motoristaUid", uidLogado()).get().await().documents
            .mapNotNull { it.paraSolicitacao() }
            .filter { it.prestadorUid != null }
            // Com mais de uma solicitação pro mesmo prestador, vale a mais recente.
            .groupBy { it.prestadorUid!! }
            .mapValues { (_, solicitacoes) -> solicitacoes.maxBy { it.timestamp?.time ?: 0L } }
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
            "status" to PENDENTE,
            "timestamp" to Date(),
            "latitudeMotorista" to latitude,
            "longitudeMotorista" to longitude
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
        colecao().document(solicitacaoId).update("status", status).await()
        Unit
    }

    override suspend fun aceitar(solicitacaoId: String): Result<Unit> = runCatching {
        atualizarStatus(solicitacaoId, ACEITO).getOrThrow()
        // O diálogo de aceite promete esse aviso ao motorista. Se o chat
        // falhar, o aceite já valeu — não desfaz por causa da mensagem.
        chatRepository.enviarMensagem(
            solicitacaoId,
            IChatRepository.PRESTADOR,
            texto = "✅ Aceitei sua solicitação e estou a caminho para o socorro.",
            imagemUrl = null
        )
        Unit
    }

    override suspend fun excluir(solicitacaoId: String): Result<Unit> = runCatching {
        colecao().document(solicitacaoId).delete().await()
        Unit
    }
}
