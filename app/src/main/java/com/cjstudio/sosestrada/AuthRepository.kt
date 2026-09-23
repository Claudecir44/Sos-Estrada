package com.cjstudio.sosestrada

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) : IAuthRepository {

    override fun uidLogado(): String? = auth.currentUser?.uid

    override fun emailLogado(): String? = auth.currentUser?.email

    override suspend fun entrar(email: String, senha: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, senha).await()
        Unit
    }

    override suspend fun criarConta(email: String, senha: String): Result<String> = runCatching {
        val resultado = auth.createUserWithEmailAndPassword(email, senha).await()
        resultado.user?.uid ?: throw IllegalStateException("Conta criada sem usuário.")
    }

    override suspend fun reautenticar(senha: String): Result<Unit> = runCatching {
        val usuario = auth.currentUser ?: throw IllegalStateException("Não há sessão ativa.")
        val email = usuario.email ?: throw IllegalStateException("E-mail não encontrado.")
        usuario.reauthenticate(EmailAuthProvider.getCredential(email, senha)).await()
        Unit
    }

    override suspend fun excluirConta(): Result<Unit> = runCatching {
        val usuario = auth.currentUser ?: throw IllegalStateException("Não há sessão ativa.")
        usuario.delete().await()
        Unit
    }
}
