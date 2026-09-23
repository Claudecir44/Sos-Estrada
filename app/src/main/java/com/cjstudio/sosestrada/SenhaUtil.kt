package com.cjstudio.sosestrada

// Regra única de senha pra todos os cadastros (motorista, prestador e admin):
// de 6 a 10 caracteres, quaisquer (letras, números ou símbolos). O mínimo de
// 6 é também o do próprio Firebase Auth.
object SenhaUtil {
    const val MINIMO = 6
    const val MAXIMO = 10

    // Mensagem de erro pro campo, ou null se a senha é válida.
    fun validar(senha: String): String? = when {
        senha.isEmpty() -> "Senha obrigatória"
        senha.length < MINIMO -> "A senha deve ter pelo menos $MINIMO caracteres"
        senha.length > MAXIMO -> "A senha deve ter no máximo $MAXIMO caracteres"
        else -> null
    }
}
