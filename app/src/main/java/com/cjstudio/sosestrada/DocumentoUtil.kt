package com.cjstudio.sosestrada

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

// CPF ou CNPJ do prestador (um campo só). O período grátis vale uma vez por
// documento — a mesma conta dos dígitos está em functions/index.js
// (cpfValido/cnpjValido).
object DocumentoUtil {

    fun somenteDigitos(valor: String?): String = CpfUtil.somenteDigitos(valor)

    fun valido(documento: String?): Boolean {
        val d = somenteDigitos(documento)
        return when (d.length) {
            11 -> CpfUtil.valido(d)
            14 -> cnpjValido(d)
            else -> false
        }
    }

    private fun cnpjValido(d: String): Boolean {
        if (d.all { it == d[0] }) return false
        val pesos = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        for (tamanho in intArrayOf(12, 13)) {
            val inicio = pesos.size - tamanho
            var soma = 0
            for (i in 0 until tamanho) soma += (d[i] - '0') * pesos[inicio + i]
            val resto = soma % 11
            val digito = if (resto < 2) 0 else 11 - resto
            if (digito != d[tamanho] - '0') return false
        }
        return true
    }

    // Até 11 dígitos formata como CPF (000.000.000-00); passando disso, como
    // CNPJ (00.000.000/0000-00). Parcial enquanto digita.
    fun formatar(documento: String?): String {
        val d = somenteDigitos(documento).take(14)
        if (d.length <= 11) return CpfUtil.formatar(d)
        val sb = StringBuilder()
        d.forEachIndexed { i, c ->
            when (i) {
                2, 5 -> sb.append('.')
                8 -> sb.append('/')
                12 -> sb.append('-')
            }
            sb.append(c)
        }
        return sb.toString()
    }

    fun aplicarMascara(campo: EditText) {
        var atualizando = false
        campo.doAfterTextChanged { texto ->
            if (atualizando) return@doAfterTextChanged
            val formatado = formatar(texto?.toString())
            if (formatado == texto?.toString()) return@doAfterTextChanged
            atualizando = true
            campo.setText(formatado)
            campo.setSelection(formatado.length)
            atualizando = false
        }
    }
}
