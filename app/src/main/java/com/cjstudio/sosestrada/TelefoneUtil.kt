package com.cjstudio.sosestrada

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

// Máscara (XX) XXXXX-XXXX usada nos cadastros de prestador e de admin.
object TelefoneUtil {

    fun formatar(valor: String?): String {
        val digitos = valor.orEmpty().filter { it.isDigit() }.take(11)
        val formatado = StringBuilder()
        if (digitos.isNotEmpty()) formatado.append("(").append(digitos.take(2))
        if (digitos.length >= 3) formatado.append(") ").append(digitos.substring(2, minOf(6, digitos.length)))
        if (digitos.length >= 7) formatado.append("-").append(digitos.substring(6))
        return formatado.toString()
    }

    fun aplicarMascara(campo: EditText) {
        var atualizando = false
        campo.doAfterTextChanged { texto ->
            if (atualizando || texto == null) return@doAfterTextChanged
            atualizando = true
            texto.replace(0, texto.length, formatar(texto.toString()))
            atualizando = false
        }
    }
}
