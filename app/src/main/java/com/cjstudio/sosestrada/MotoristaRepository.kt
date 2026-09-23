package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

    override suspend fun salvarMeuCadastro(motorista: Motorista, cadastroNovo: Boolean): Result<Unit> = runCatching {
        val uid = uidLogado()
        // Mapa explícito (e não o objeto): grava exatamente os campos de
        // sempre, com o uid do documento, sem depender do que vier no objeto.
        val dados = mutableMapOf<String, Any?>(
            "uid" to uid,
            "nome" to motorista.nome,
            "telefone" to motorista.telefone,
            "email" to motorista.email,
            "veiculo" to motorista.veiculo,
            "placa" to motorista.placa,
            "cor" to motorista.cor
        )
        if (!motorista.foto.isNullOrEmpty()) dados["foto"] = motorista.foto
        val documento = db.collection("motoristas").document(uid)
        if (cadastroNovo) documento.set(dados).await() else documento.set(dados, SetOptions.merge()).await()
        Unit
    }

    override suspend fun sincronizarEmail(emailDoLogin: String) {
        runCatching {
            val atual = meuDocumento().get().await().getString("email")
            if (atual != null && !atual.equals(emailDoLogin, ignoreCase = true)) {
                meuDocumento().update("email", emailDoLogin).await()
            }
        }
    }

    override suspend fun excluirMeuCadastro(): Result<Unit> = runCatching {
        meuDocumento().delete().await()
        Unit
    }
}
