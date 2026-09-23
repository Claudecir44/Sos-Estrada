package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotoristaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IMotoristaRepository {

    private fun uidLogado(): String = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

    private fun meuDocumento() = db.collection("motoristas").document(uidLogado())

    override suspend fun buscarMeuCadastro(): Result<Motorista?> = runCatching {
        meuDocumento().get().await().toObject(Motorista::class.java)
    }

    override suspend fun salvarMeuCadastro(motorista: Motorista): Result<Unit> = runCatching {
        val uid = uidLogado()
        // Mapa explícito (e não o objeto): grava exatamente os campos de
        // sempre, com o uid do documento, sem depender do que vier no objeto.
        val dados = mapOf(
            "uid" to uid,
            "nome" to motorista.nome,
            "telefone" to motorista.telefone,
            "email" to motorista.email,
            "veiculo" to motorista.veiculo,
            "placa" to motorista.placa,
            "cor" to motorista.cor
        )
        db.collection("motoristas").document(uid).set(dados).await()
        Unit
    }

    override suspend fun excluirMeuCadastro(): Result<Unit> = runCatching {
        meuDocumento().delete().await()
        Unit
    }
}
