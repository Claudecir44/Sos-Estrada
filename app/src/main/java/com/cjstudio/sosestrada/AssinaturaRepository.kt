package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
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
        expiraEm = getTimestamp("assinaturaExpiraEm")?.toDate()?.time
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

    override suspend fun criarCheckout(): Result<String> = runCatching {
        val resultado = functions.getHttpsCallable("criarPreferenciaPagamentoPrestador").call().await()
        val initPoint = (resultado.data as? Map<*, *>)?.get("initPoint") as? String
        if (initPoint.isNullOrEmpty()) throw IllegalStateException("Não foi possível iniciar o pagamento.")
        initPoint
    }
}
