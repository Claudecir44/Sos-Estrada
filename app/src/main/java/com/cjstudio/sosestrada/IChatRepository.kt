package com.cjstudio.sosestrada

import android.net.Uri
import kotlinx.coroutines.flow.Flow

// Chat motorista <-> prestador dentro de uma solicitação
// (solicitacoes/{id}/mensagens) + contadores de não lidas no documento pai.
interface IChatRepository {
    // Mensagens em tempo real, da mais antiga pra mais nova.
    fun escutarMensagens(solicitacaoId: String): Flow<List<Mensagem>>

    // Grava a mensagem e soma 1 no contador de não lidas do outro lado.
    suspend fun enviarMensagem(solicitacaoId: String, meuTipo: String, texto: String?, imagemUrl: String?): Result<Unit>

    // Sobe a foto pro Storage e devolve a URL pública.
    suspend fun enviarImagem(solicitacaoId: String, imagem: Uri): Result<String>

    // Marca como lidas as mensagens do outro lado que ainda não foram lidas.
    suspend fun marcarComoLidas(solicitacaoId: String, mensagens: List<Mensagem>, meuTipo: String)

    suspend fun zerarNaoLidas(solicitacaoId: String, meuTipo: String)

    // Apaga mensagens (e fotos) com mais de 6 meses.
    suspend fun limparMensagensAntigas(solicitacaoId: String)

    companion object {
        const val MOTORISTA = "motorista"
        const val PRESTADOR = "prestador"
    }
}
