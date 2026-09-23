package com.cjstudio.sosestrada

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrestadorRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) : IPrestadorRepository {

    private fun uidLogado(): String = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

    private fun meuDocumento() = db.collection("prestadores").document(uidLogado())

    override suspend fun buscarMeuCadastro(): Result<Prestador?> = runCatching {
        meuDocumento().get().await().toObject(Prestador::class.java)
    }

    override suspend fun salvarMeuCadastro(prestador: Prestador, cadastroNovo: Boolean): Result<Unit> = runCatching {
        val uid = uidLogado()
        val dados = mutableMapOf<String, Any?>(
            "uid" to uid,
            "nome" to prestador.nome,
            "cnpj" to prestador.cnpj,
            "telefone" to prestador.telefone,
            "email" to prestador.email,
            "servico" to prestador.servico,
            "preco" to prestador.preco,
            "rua" to prestador.rua,
            "numero" to prestador.numero,
            "bairro" to prestador.bairro,
            "cidade" to prestador.cidade,
            "complemento" to prestador.complemento,
            "estado" to prestador.estado,
            "pais" to prestador.pais
        )
        if (!prestador.logo.isNullOrEmpty()) dados["logo"] = prestador.logo

        val documento = db.collection("prestadores").document(uid)
        if (cadastroNovo) {
            documento.set(dados).await()
        } else {
            // merge: um set() completo apagaria ativo/dataCadastro/assinatura*
            // (gravados pela Cloud Function) e as regras negariam a edição.
            documento.set(dados, SetOptions.merge()).await()
        }
        Unit
    }

    override suspend fun enviarLogo(imagem: Uri): Result<String> = runCatching {
        val referencia = storage.reference.child("logos/${uidLogado()}/${UUID.randomUUID()}.jpg")
        referencia.putFile(imagem).await()
        referencia.downloadUrl.await().toString()
    }

    override suspend fun excluirMeuCadastro(): Result<Unit> = runCatching {
        meuDocumento().delete().await()
        Unit
    }

    // "ativo" só existe em prestadores criados depois do módulo de assinatura
    // (Cloud Function aoRegistrarPrestador) ou migrados por
    // migrarAssinaturaPrestadores. Enquanto a migração não rodar em produção
    // (exige o plano Blaze), cadastros antigos não aparecem aqui.
    override suspend fun listarAtivos(): Result<List<Prestador>> = runCatching {
        db.collection("prestadores").whereEqualTo("ativo", true).get().await()
            .documents.mapNotNull { it.toObject(Prestador::class.java) }
    }
}
