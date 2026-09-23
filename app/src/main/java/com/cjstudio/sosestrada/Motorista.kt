package com.cjstudio.sosestrada

// Documento motoristas/{uid}. Valores padrão = construtor vazio exigido pelo
// Firestore no toObject().
data class Motorista(
    var uid: String? = null,
    var nome: String? = null,
    var telefone: String? = null,
    var email: String? = null,
    var veiculo: String? = null,
    var placa: String? = null,
    var cor: String? = null,
    // JPEG pequeno em base64 (ver FotoUtil) — não há Firebase Storage.
    var foto: String? = null,
    // Bloqueado pelo admin: não entra, não edita e não pede socorro.
    var bloqueado: Boolean = false
)
