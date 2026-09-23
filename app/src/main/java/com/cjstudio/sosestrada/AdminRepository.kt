package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val authRepository: IAuthRepository
) : IAdminRepository {

    private fun documentoAdmin(uid: String) = db.collection("admins").document(uid)

    // Quem garante o acesso de verdade são as regras (ehAdmin()); aqui é só
    // pra barrar logo no login quem não é admin, com uma mensagem clara.
    private suspend fun ehAdmin(uid: String): Boolean =
        runCatching { documentoAdmin(uid).get().await().exists() }.getOrDefault(false)

    override suspend fun temSessaoDeAdmin(): Boolean {
        val usuario = auth.currentUser ?: return false
        if (usuario.isAnonymous || !usuario.isEmailVerified) return false
        return ehAdmin(usuario.uid)
    }

    override suspend fun entrar(email: String, senha: String): Result<Unit> = runCatching {
        // Sessão antiga (login fixo + anônimo das versões anteriores) não serve mais.
        if (auth.currentUser?.isAnonymous == true) auth.signOut()
        authRepository.entrar(email, senha).getOrThrow()
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Falha ao entrar.")
        if (!ehAdmin(uid)) {
            auth.signOut()
            throw IllegalStateException("Esta conta não tem acesso ao painel administrativo.")
        }
        Unit
    }

    override suspend fun cadastrarAdmin(admin: Admin, senha: String, senhaMaster: String): Result<Unit> = runCatching {
        if (auth.currentUser?.isAnonymous == true) auth.signOut()
        // O Firebase guarda o e-mail em minúsculas no token, e as regras
        // comparam o do cadastro com o do token.
        val email = admin.email.orEmpty().trim().lowercase()
        // E-mail que já tem conta (ex.: a mesma pessoa já é motorista ou
        // prestador): em vez de dar "e-mail já existe", entra nessa conta
        // com a senha digitada e só acrescenta o cadastro de admin.
        var contaNova = true
        val uid = try {
            authRepository.criarConta(email, senha).getOrThrow()
        } catch (e: FirebaseAuthUserCollisionException) {
            contaNova = false
            runCatching { auth.signInWithEmailAndPassword(email, senha).await() }.getOrElse {
                throw IllegalStateException("Este e-mail já tem uma conta com outra senha. Use a senha dessa conta (ou \"Esqueci minha senha\" no login).")
            }
            auth.currentUser?.uid ?: throw IllegalStateException("Falha ao entrar na conta existente.")
        }
        try {
            // As regras só aceitam criar admins/{uid} com a senha master certa
            // (comparam o hash dela) — errada, a gravação é negada.
            val dados = mapOf(
                "nome" to admin.nome,
                "sobrenome" to admin.sobrenome,
                "email" to email,
                "telefone" to admin.telefone,
                "cpf" to admin.cpf,
                "foto" to admin.foto,
                "criadoEm" to FieldValue.serverTimestamp(),
                CAMPO_AUTORIZACAO to senhaMaster
            )
            documentoAdmin(uid).set(dados).await()
        } catch (e: Exception) {
            // Conta criada agora sem o cadastro de admin não serve pra nada:
            // desfaz. Conta que já existia antes fica como estava.
            if (contaNova) runCatching { authRepository.excluirConta() }
            authRepository.sair()
            val permissaoNegada = e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            throw if (permissaoNegada) SenhaMasterIncorretaException() else e
        }
        // A senha master só serviu pra autorizar a criação — não fica guardada.
        runCatching { documentoAdmin(uid).update(CAMPO_AUTORIZACAO, FieldValue.delete()).await() }
        if (auth.currentUser?.isEmailVerified != true) authRepository.enviarVerificacaoEmail()
        authRepository.sair()
        Unit
    }

    override suspend fun enviarRedefinicaoSenha(email: String): Result<Unit> = authRepository.enviarRedefinicaoSenha(email)

    override fun sair() = authRepository.sair()

    override fun emailLogado(): String? = auth.currentUser?.email

    override suspend fun buscarMeuCadastro(): Result<Admin?> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
        documentoAdmin(uid).get().await().toObject(Admin::class.java)
    }

    override suspend fun atualizarMeuCadastro(admin: Admin): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
        val dados = mutableMapOf<String, Any?>(
            "nome" to admin.nome,
            "sobrenome" to admin.sobrenome,
            "telefone" to admin.telefone,
            "cpf" to admin.cpf
        )
        // Sem foto nova, fica a que já estava.
        if (!admin.foto.isNullOrEmpty()) dados["foto"] = admin.foto
        documentoAdmin(uid).update(dados).await()
        Unit
    }

    override suspend fun excluirMeuCadastro(senha: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
        authRepository.reautenticar(senha).getOrElse { throw IllegalArgumentException("Senha incorreta.") }
        // A mesma conta pode ser também motorista/prestador: nesse caso só o
        // acesso de admin sai, e o login continua valendo pro outro perfil.
        val temOutroPerfil = db.collection("motoristas").document(uid).get().await().exists() ||
            db.collection("prestadores").document(uid).get().await().exists()
        documentoAdmin(uid).delete().await()
        if (!temOutroPerfil) authRepository.excluirConta().getOrThrow()
        authRepository.sair()
        Unit
    }

    override suspend fun listarMotoristas(): Result<List<Motorista>> = runCatching {
        db.collection("motoristas").get().await().documents
            .mapNotNull { it.toObject(Motorista::class.java) }
            .sortedBy { it.nome?.lowercase() }
    }

    override suspend fun listarPrestadores(): Result<List<Prestador>> = runCatching {
        db.collection("prestadores").get().await().documents
            .mapNotNull { it.toObject(Prestador::class.java) }
            .sortedBy { it.nome?.lowercase() }
    }

    override suspend fun listarSolicitacoes(): Result<List<Solicitacao>> = runCatching {
        db.collection("solicitacoes").get().await().documents
            .mapNotNull { doc -> doc.toObject(Solicitacao::class.java)?.also { it.id = doc.id } }
            .sortedByDescending { it.timestamp?.time ?: 0L }
    }

    override suspend fun editarMotorista(uid: String, dados: Map<String, Any?>, senhaMaster: String): Result<Unit> =
        executarAcao(COLECAO_MOTORISTAS, uid, "editar", senhaMaster) { lote, alvo -> lote.update(alvo, dados) }

    override suspend fun editarPrestador(uid: String, dados: Map<String, Any?>, senhaMaster: String): Result<Unit> =
        executarAcao(COLECAO_PRESTADORES, uid, "editar", senhaMaster) { lote, alvo -> lote.update(alvo, dados) }

    override suspend fun definirBloqueio(colecao: String, uid: String, bloquear: Boolean, senhaMaster: String): Result<Unit> =
        executarAcao(colecao, uid, "bloquear", senhaMaster) { lote, alvo -> lote.update(alvo, "bloqueado", bloquear) }

    override suspend fun excluirCadastro(colecao: String, uid: String, senhaMaster: String): Result<Unit> =
        executarAcao(colecao, uid, "excluir", senhaMaster) { lote, alvo -> lote.delete(alvo) }

    // A mudança vai num lote junto com acoesAdmin/{uid}, que carrega a senha
    // master digitada: as regras só aceitam esse registro se o hash da senha
    // bater, e só aceitam a mudança no cadastro se o registro foi gravado no
    // MESMO lote (ver acaoAdminAutorizada em firestore.rules). Depois o
    // registro é apagado — a senha não fica guardada.
    private suspend fun executarAcao(
        colecao: String,
        uid: String,
        acao: String,
        senhaMaster: String,
        mudanca: (WriteBatch, DocumentReference) -> Unit
    ): Result<Unit> = runCatching {
        val adminUid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
        val registro = db.collection("acoesAdmin").document(uid)
        val lote = db.batch()
        lote.set(
            registro,
            mapOf(
                "adminUid" to adminUid,
                "acao" to acao,
                "colecao" to colecao,
                CAMPO_AUTORIZACAO to senhaMaster,
                "em" to FieldValue.serverTimestamp()
            )
        )
        mudanca(lote, db.collection(colecao).document(uid))
        try {
            lote.commit().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw SenhaMasterIncorretaException()
            throw e
        }
        runCatching { registro.delete().await() }
        Unit
    }

    companion object {
        const val COLECAO_MOTORISTAS = "motoristas"
        const val COLECAO_PRESTADORES = "prestadores"

        // Campo temporário com a senha master digitada; as regras conferem o
        // hash dele na criação e só deixam o próprio admin apagá-lo depois.
        private const val CAMPO_AUTORIZACAO = "autorizacao"
    }
}
