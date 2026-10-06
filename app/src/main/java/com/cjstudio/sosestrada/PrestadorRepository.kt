package com.cjstudio.sosestrada

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrestadorRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
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
        if (prestador.sexo != null) dados["sexo"] = prestador.sexo
        dados["atendeSexo"] = prestador.atendeSexo ?: SexoUtil.AMBOS
        // Depois de gravado o documento não muda (firestore.rules).
        if (!prestador.documento.isNullOrEmpty()) dados["documento"] = prestador.documento

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

    override suspend fun verificarDocumento(documento: String): Result<String> = runCatching {
        val resultado = functions.getHttpsCallable("verificarDocumentoPrestador")
            .call(mapOf("documento" to documento)).await()
        (resultado.data as? Map<*, *>)?.get("situacao") as? String ?: "livre"
    }

    override suspend fun excluirMeuCadastro(): Result<Unit> = runCatching {
        meuDocumento().delete().await()
        Unit
    }

    // Só some da busca quem as Cloud Functions marcaram ativo == false (trial
    // ou assinatura vencida). Cadastros sem o campo (anteriores ao módulo de
    // assinatura, ou criados antes das functions estarem publicadas) aparecem
    // normalmente — antes, whereEqualTo("ativo", true) escondia todos eles.
    override suspend fun listarAtivos(): Result<List<Prestador>> = runCatching {
        db.collection("prestadores").get().await()
            .documents.mapNotNull { doc ->
                // Cadastros antigos podem não ter o campo uid gravado.
                doc.toObject(Prestador::class.java)?.apply { if (uid.isNullOrEmpty()) uid = doc.id }
            }
            .filter { !it.bloqueado && it.ativo != false }
    }

    override suspend fun definirSexo(sexo: String): Result<Unit> = runCatching {
        db.collection("prestadores").document(uidLogado()).update("sexo", sexo).await()
        Unit
    }
}
