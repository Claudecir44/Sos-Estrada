package com.cjstudio.sosestrada

import com.google.firebase.firestore.Exclude

// Documento prestadores/{uid}. Os campos do fim (distancia em diante) não
// vêm do Firestore: são preenchidos na busca do motorista (SocorroActivity).
data class Prestador(
    var uid: String? = null,
    var nome: String? = null,
    var cnpj: String? = null,
    var telefone: String? = null,
    var email: String? = null,
    var servico: String? = null,
    var preco: String? = null,
    var logo: String? = null,
    // Campos antigos de endereço (cadastros anteriores aos campos separados)
    var localizacao: String? = null,
    var endereco: String? = null,
    var rua: String? = null,
    var numero: String? = null,
    var bairro: String? = null,
    var cidade: String? = null,
    var complemento: String? = null,
    var estado: String? = null,
    var pais: String? = null,
    var distancia: Double = 0.0,
    // Status da solicitação com este prestador (pendente, aceito, recusado ou null)
    var statusSolicitacao: String? = null,
    // Id da solicitação ativa com este prestador (usado para abrir o chat)
    var solicitacaoId: String? = null,
    // Mensagens do prestador ainda não lidas pelo motorista, na solicitação ativa.
    var naoLidasMotorista: Int = 0
) {
    // Endereço montado dos campos novos; sem eles, cai nos campos antigos.
    @get:Exclude
    val enderecoCompleto: String
        get() {
            val partes = StringBuilder()
            if (!rua.isNullOrEmpty()) partes.append(rua)
            if (!numero.isNullOrEmpty()) partes.append(", ").append(numero)
            if (!bairro.isNullOrEmpty()) partes.append(" - ").append(bairro)
            if (!cidade.isNullOrEmpty()) partes.append(", ").append(cidade)
            if (!estado.isNullOrEmpty()) partes.append(" - ").append(estado)
            if (!pais.isNullOrEmpty()) partes.append(", ").append(pais)
            if (!complemento.isNullOrEmpty()) partes.append(" (").append(complemento).append(")")
            if (partes.isNotEmpty()) return partes.toString()
            return endereco?.takeIf { it.isNotEmpty() }
                ?: localizacao?.takeIf { it.isNotEmpty() }
                ?: "Endereço não informado"
        }
}
