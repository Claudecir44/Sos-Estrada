package com.cjstudio.sosestrada

import com.cjstudio.sosestrada.ITermosRepository.Companion.CAMPO_ACEITO_EM
import com.cjstudio.sosestrada.ITermosRepository.Companion.CAMPO_VERSAO
import com.cjstudio.sosestrada.ITermosRepository.Companion.VERSAO_TERMOS
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermosRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ITermosRepository {

    private fun documento(perfil: String) =
        db.collection(perfil).document(auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa."))

    override suspend fun aceitou(perfil: String): Result<Boolean> = runCatching {
        documento(perfil).get().await().getString(CAMPO_VERSAO) == VERSAO_TERMOS
    }

    override suspend fun registrarAceite(perfil: String): Result<Unit> = runCatching {
        documento(perfil).update(
            mapOf(CAMPO_VERSAO to VERSAO_TERMOS, CAMPO_ACEITO_EM to FieldValue.serverTimestamp())
        ).await()
        Unit
    }
}
