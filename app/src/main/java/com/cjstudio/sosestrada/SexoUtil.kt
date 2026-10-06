package com.cjstudio.sosestrada

import android.app.Activity
import android.app.AlertDialog
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

// Sexo do usuário e as preferências ligadas a ele (igual ao Caronas):
// - Motorista.sexo / Prestador.sexo: escolhido no cadastro; contas antigas
//   completam no próximo login (exigirSexo). Depois de gravado, só o admin
//   muda (firestore.rules).
// - Prestador.atendeSexo: quais motoristas o prestador atende.
// - Busca do motorista: filtro "Prestador: Homem/Mulher/Ambos".
// Valores gravados no Firestore: "homem", "mulher", "ambos" (as regras
// conferem os mesmos). Sem atendeSexo = atende todos.
object SexoUtil {
    const val HOMEM = "homem"
    const val MULHER = "mulher"
    const val AMBOS = "ambos"

    fun rotulo(sexo: String?) = when (sexo) {
        HOMEM -> "Homem"
        MULHER -> "Mulher"
        else -> "Não informado"
    }

    // O prestador atende o sexo deste motorista? (mesma regra de
    // firestore.rules, que é a trava de verdade no pedido de socorro).
    fun prestadorAtende(prestador: Prestador, sexoMotorista: String?): Boolean {
        val atende = prestador.atendeSexo ?: AMBOS
        return atende == AMBOS || atende == sexoMotorista
    }

    // Filtro "Prestador: Homem/Mulher/Ambos" da busca.
    fun prestadorCombina(prestador: Prestador, filtro: String) = filtro == AMBOS || prestador.sexo == filtro

    // Texto curto pro cartão do prestador, null quando atende todos.
    fun rotuloRestricao(atendeSexo: String?) = when (atendeSexo) {
        HOMEM -> "👨 Atende só homens"
        MULHER -> "👩 Atende só mulheres"
        else -> null
    }

    // Lê um RadioGroup de sexo cujos botões têm tag = "homem"/"mulher"/"ambos".
    fun valorMarcado(grupo: RadioGroup): String? =
        grupo.findViewById<RadioButton>(grupo.checkedRadioButtonId)?.tag as? String

    fun marcar(grupo: RadioGroup, valor: String?) {
        for (i in 0 until grupo.childCount) {
            val botao = grupo.getChildAt(i) as? RadioButton ?: continue
            if (botao.tag == valor) botao.isChecked = true
        }
    }

    // Conta antiga, de antes do campo sexo: completa o cadastro aqui, num
    // aviso que não fecha sem escolher. Grava uma vez só.
    fun exigirSexo(activity: Activity, salvar: suspend (String) -> Result<Unit>, executar: (suspend () -> Unit) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Complete seu cadastro")
            .setMessage(
                "Agora o motorista pode escolher se quer ser atendido por prestador homem, mulher ou ambos, " +
                    "e o prestador escolhe quem atende. Para continuar usando o app, informe seu sexo. " +
                    "Depois, só o suporte altera."
            )
            .setCancelable(false)
            .setNegativeButton("Homem") { _, _ -> gravar(activity, HOMEM, salvar, executar) }
            .setPositiveButton("Mulher") { _, _ -> gravar(activity, MULHER, salvar, executar) }
            .show()
    }

    private fun gravar(activity: Activity, sexo: String, salvar: suspend (String) -> Result<Unit>, executar: (suspend () -> Unit) -> Unit) {
        executar {
            salvar(sexo).onFailure { e ->
                Toast.makeText(activity, "Não foi possível salvar: ${e.message}", Toast.LENGTH_LONG).show()
                exigirSexo(activity, salvar, executar)
            }
        }
    }
}
