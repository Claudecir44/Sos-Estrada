package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssinaturaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : IAssinaturaRepository {

    private fun meuDocumento() = db.collection("prestadores")
        .document(auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa."))

    private fun DocumentSnapshot.paraStatus() = StatusAssinatura(
        status = getString("assinaturaStatus") ?: "trial",
        dataCadastro = getTimestamp("dataCadastro")?.toDate()?.time,
        expiraEm = getTimestamp("assinaturaExpiraEm")?.toDate()?.time,
        ativo = getBoolean("ativo"),
        bloqueado = getBoolean("bloqueado") == true,
        diasTotal = getLong("assinaturaDiasTotal")?.toInt(),
        trialNegado = getBoolean("trialNegado") == true
    )

    override suspend fun buscarMinhaAssinatura(): Result<StatusAssinatura> = runCatching {
        meuDocumento().get().await().paraStatus()
    }

    override fun escutarMinhaAssinatura(): Flow<StatusAssinatura> = callbackFlow {
        val registro = meuDocumento().addSnapshotListener { snapshot, erro ->
            if (erro != null || snapshot == null) return@addSnapshotListener
            trySend(snapshot.paraStatus())
        }
        awaitClose { registro.remove() }
    }

    override suspend fun criarCheckout(plano: String, externalTransactionToken: String?): Result<String> = runCatching {
        val parametros = mutableMapOf<String, Any>("plano" to plano)
        if (externalTransactionToken != null) parametros["externalTransactionToken"] = externalTransactionToken
        val resultado = functions.getHttpsCallable("criarPreferenciaPagamentoPrestador").call(parametros).await()
        val initPoint = (resultado.data as? Map<*, *>)?.get("initPoint") as? String
        if (initPoint.isNullOrEmpty()) throw IllegalStateException("Não foi possível iniciar o pagamento.")
        initPoint
    }

    override suspend fun confirmarCompraGooglePlay(purchaseToken: String, productId: String): Result<Boolean> = runCatching {
        val resultado = try {
            functions.getHttpsCallable("confirmarCompraGooglePlayPrestador")
                .call(mapOf("purchaseToken" to purchaseToken, "productId" to productId)).await()
        } catch (e: FirebaseFunctionsException) {
            throw IllegalStateException(e.message ?: "Não foi possível confirmar a compra.")
        }
        (resultado.data as? Map<*, *>)?.get("pendente") == true
    }
}
