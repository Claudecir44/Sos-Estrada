package com.cjstudio.sosestrada

// Documento admins/{uid}: quem tem acesso ao painel administrativo. Só é
// criado com a senha do administrador master (conferida pelas regras do
// Firestore — ver firestore.rules, match /admins).
data class Admin(
    var nome: String? = null,
    var sobrenome: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var cpf: String? = null,
    // JPEG pequeno em base64 (ver FotoUtil) — não há Firebase Storage.
    var foto: String? = null
)
