package com.cjstudio.sosestrada

import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
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

    override suspend fun perfisDaConta(): Set<String> {
        val uid = auth.currentUser?.uid ?: return emptySet()
        suspend fun tem(colecao: String) = db.collection(colecao).document(uid).get().await().exists()
        return buildSet {
            if (tem("motoristas")) add(IChatRepository.MOTORISTA)
            if (tem("prestadores")) add(IChatRepository.PRESTADOR)
        }
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
        val resultado = try {
            auth.createUserWithEmailAndPassword(email, senha).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            throw IllegalStateException(
                "Este e-mail já está cadastrado no SOS Estrada. Cada e-mail vale para um perfil só " +
                    "(motorista ou prestador): entre pela opção certa ou use outro e-mail."
            )
        }
        resultado.user?.uid ?: throw IllegalStateException("Conta criada sem usuário.")
    }

    // Primeiro e-mail, logo depois do cadastro. Grava a hora pra o login em
    // seguida não mandar outro (o link novo invalidaria este).
    override suspend fun enviarVerificacaoEmail(): Result<Unit> = runCatching {
        val usuario = auth.currentUser ?: throw IllegalStateException("Não há sessão ativa.")
        enviarVerificacao(usuario, ORIGEM_CADASTRO)
        prefs.edit().putLong(KEY_ULTIMO_ENVIO, System.currentTimeMillis()).apply()
        Unit
    }

    override suspend fun enviarRedefinicaoSenha(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    // Precisa da senha porque o Firebase só manda o e-mail de verificação pra
    // um usuário logado (no Caronas o reenvio sem senha depende de uma Cloud
    // Function com permissão extra no Google Cloud, que nunca foi liberada).
    override suspend fun reenviarValidacao(email: String, senha: String): Result<String> = runCatching {
        val usuario = auth.signInWithEmailAndPassword(email.trim(), senha).await().user
            ?: throw IllegalStateException("Não foi possível entrar com esse e-mail.")
        try {
            usuario.reload().await()
            if (usuario.isEmailVerified) {
                "Este cadastro já está validado. Pode entrar normalmente."
            } else {
                reenviarVerificacaoComIntervalo(usuario, INTERVALO_SUPORTE_MS, ORIGEM_CADASTRO)
                    .replace("Valide seu cadastro pelo e-mail para poder entrar. ", "")
            }
        } finally {
            auth.signOut()
        }
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
        // Conta que também é admin (o admin master, sobretudo) mantém o login:
        // excluir o cadastro de motorista/prestador não pode derrubar o acesso
        // ao painel. Mesma regra do servidor (apagarLoginSeSobrouNada).
        if (db.collection("admins").document(usuario.uid).get().await().exists()) return@runCatching
        try {
            usuario.delete().await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // O servidor apaga o login junto com o cadastro
            // (apagarLogin*Excluido em functions/index.js) e às vezes chega
            // antes daqui — o login já não existe, então está feito.
        }
        Unit
    }

    override fun sair() = auth.signOut()

    // Mesmo cuidado do Caronas (VerificacaoEmailUtil): cada e-mail novo gera
    // um link novo e o Firebase invalida o anterior. Com o reenvio a cada 1
    // minuto, quem tentava entrar antes de abrir o e-mail recebia outro, abria
    // o primeiro e via "link expirado" (erro real no Caronas). Agora o login
    // só manda outro depois de 1 hora (aqui e no servidor, que não se perde
    // ao reinstalar); o Suporte, que é pedido explícito, depois de 2 min.
    private suspend fun reenviarVerificacaoComIntervalo(
        usuario: FirebaseUser,
        intervaloMs: Long = INTERVALO_REENVIO_MS,
        origem: String = ORIGEM_LOGIN
    ): String {
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong(KEY_ULTIMO_ENVIO, 0L) < intervaloMs) return MENSAGEM_RECENTE
        return try {
            when (enviarVerificacao(usuario, origem)) {
                ResultadoEnvio.RECENTE -> MENSAGEM_RECENTE
                ResultadoEnvio.JA_VERIFICADO -> "Este cadastro já está validado. Pode entrar normalmente."
                ResultadoEnvio.ENVIADO -> {
                    prefs.edit().putLong(KEY_ULTIMO_ENVIO, agora).apply()
                    "Valide seu cadastro pelo e-mail para poder entrar. Reenviamos o e-mail de verificação — confira também a caixa de spam e use sempre o e-mail mais recente."
                }
            }
        } catch (e: Exception) {
            val erro = e.message.orEmpty()
            if (erro.contains("TOO_MANY", ignoreCase = true) || erro.contains("too-many-requests", ignoreCase = true)) {
                "Você pediu e-mails de verificação demais recentemente. Aguarde alguns minutos e confira o spam do e-mail já enviado."
            } else {
                "Valide seu cadastro pelo e-mail para poder entrar. Não conseguimos confirmar o reenvio agora — confira a caixa de entrada e o spam do e-mail já enviado."
            }
        }
    }

    private enum class ResultadoEnvio { ENVIADO, RECENTE, JA_VERIFICADO }

    // E-mail próprio em português pelo servidor (enviarVerificacaoEmailPropria);
    // se ele não puder mandar (sem configuração de e-mail ou fora do ar), cai
    // no e-mail padrão do Firebase e avisa o servidor pra contar a hora.
    private suspend fun enviarVerificacao(usuario: FirebaseUser, origem: String): ResultadoEnvio {
        val resposta = runCatching {
            functions.getHttpsCallable("enviarVerificacaoEmailPropria")
                .call(mapOf("origem" to origem)).await().getData() as? Map<*, *>
        }.getOrNull()
        return when {
            resposta?.get("enviado") == true -> ResultadoEnvio.ENVIADO
            resposta?.get("recente") == true -> ResultadoEnvio.RECENTE
            resposta?.get("jaVerificado") == true -> ResultadoEnvio.JA_VERIFICADO
            else -> {
                usuario.sendEmailVerification().await()
                runCatching {
                    functions.getHttpsCallable("enviarVerificacaoEmailPropria")
                        .call(mapOf("origem" to origem, "registrarEnvioPadrao" to true)).await()
                }
                ResultadoEnvio.ENVIADO
            }
        }
    }

    companion object {
        const val COLECAO_BLOQUEADOS = "bloqueados"
        private const val PREFS = "sos_estrada_auth"
        private const val KEY_ULTIMO_ENVIO = "ultimo_envio_verificacao"
        private const val INTERVALO_REENVIO_MS = 60 * 60_000L
        private const val INTERVALO_SUPORTE_MS = 2 * 60_000L
        private const val ORIGEM_CADASTRO = "cadastro"
        private const val ORIGEM_LOGIN = "login"
        private const val MENSAGEM_RECENTE = "Valide seu cadastro pelo e-mail para poder entrar. Já enviamos um e-mail de verificação há menos de 1 hora — abra o link dele (confira a caixa de entrada e o spam). Pedir outro agora faria o link anterior parar de funcionar."
    }
}
