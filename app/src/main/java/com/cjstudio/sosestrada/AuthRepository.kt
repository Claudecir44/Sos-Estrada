package com.cjstudio.sosestrada

import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : IAuthRepository {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun uidLogado(): String? = auth.currentUser?.uid

    override fun emailLogado(): String? = auth.currentUser?.email

    override suspend fun contaBloqueada(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return runCatching { db.collection(COLECAO_BLOQUEADOS).document(uid).get().await().exists() }
            .getOrDefault(false)
    }

    override suspend fun entrar(email: String, senha: String): Result<Unit> = runCatching {
        val usuario = auth.signInWithEmailAndPassword(email, senha).await().user
            ?: throw IllegalStateException("Falha ao entrar.")
        // reload(): a validação acontece fora do app (no navegador), e o
        // usuário devolvido pelo signIn pode ainda não refletir isso — sem
        // ele, quem já validou continuava sendo barrado (bug visto no Caronas).
        usuario.reload().await()
        if (!usuario.isEmailVerified) {
            val mensagem = reenviarVerificacaoComIntervalo(usuario)
            auth.signOut()
            throw EmailNaoVerificadoException(mensagem)
        }
        Unit
    }

    override suspend fun criarConta(email: String, senha: String): Result<String> = runCatching {
        SenhaUtil.validar(senha)?.let { throw IllegalArgumentException(it) }
        val resultado = auth.createUserWithEmailAndPassword(email, senha).await()
        resultado.user?.uid ?: throw IllegalStateException("Conta criada sem usuário.")
    }

    override suspend fun enviarVerificacaoEmail(): Result<Unit> = runCatching {
        val usuario = auth.currentUser ?: throw IllegalStateException("Não há sessão ativa.")
        usuario.sendEmailVerification().await()
        prefs.edit().putLong(KEY_ULTIMO_ENVIO, System.currentTimeMillis()).apply()
        Unit
    }

    override suspend fun enviarRedefinicaoSenha(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun trocarEmail(novoEmail: String, senha: String): Result<Unit> = runCatching {
        reautenticar(senha).getOrElse { throw IllegalArgumentException("Senha incorreta.") }
        val usuario = auth.currentUser ?: throw IllegalStateException("Não há sessão ativa.")
        usuario.verifyBeforeUpdateEmail(novoEmail.trim().lowercase()).await()
        Unit
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

    override fun sair() = auth.signOut()

    // Mesmo cuidado do Caronas (VerificacaoEmailUtil): sem um intervalo
    // mínimo, logins repetidos estouravam o limite de envio do Firebase e a
    // tela continuava dizendo "reenviamos" sem nenhum e-mail sair. A
    // mensagem devolvida diz o que realmente aconteceu.
    private suspend fun reenviarVerificacaoComIntervalo(usuario: FirebaseUser): String {
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong(KEY_ULTIMO_ENVIO, 0L) < INTERVALO_REENVIO_MS) {
            return "Valide seu cadastro pelo e-mail para poder entrar. Já enviamos um e-mail de verificação há pouco — confira a caixa de entrada e o spam antes de pedir outro."
        }
        return try {
            usuario.sendEmailVerification().await()
            prefs.edit().putLong(KEY_ULTIMO_ENVIO, agora).apply()
            "Valide seu cadastro pelo e-mail para poder entrar. Reenviamos o e-mail de verificação — confira também a caixa de spam."
        } catch (e: Exception) {
            val erro = e.message.orEmpty()
            if (erro.contains("TOO_MANY", ignoreCase = true) || erro.contains("too-many-requests", ignoreCase = true)) {
                "Você pediu e-mails de verificação demais recentemente. Aguarde alguns minutos e confira o spam do e-mail já enviado."
            } else {
                "Valide seu cadastro pelo e-mail para poder entrar. Não conseguimos confirmar o reenvio agora — confira a caixa de entrada e o spam do e-mail já enviado."
            }
        }
    }

    companion object {
        const val COLECAO_BLOQUEADOS = "bloqueados"
        private const val PREFS = "sos_estrada_auth"
        private const val KEY_ULTIMO_ENVIO = "ultimo_envio_verificacao"
        private const val INTERVALO_REENVIO_MS = 60_000L
    }
}
