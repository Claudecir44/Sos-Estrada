package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// Aceite obrigatório dos Termos de uso e privacidade na primeira entrada de
// cada perfil (motorista, prestador, admin ou colaborador) — e de novo
// quando ITermosRepository.VERSAO_TERMOS mudar. Sem aceitar, a pessoa sai.
object TermosAceiteDialogUtil {

    // Confere o aceite do perfil e, se faltar, mostra o diálogo. aoRecusar:
    // a tela encerra a sessão (cada uma tem o seu jeito de sair).
    fun exigir(
        activity: AppCompatActivity,
        perfil: String,
        repositorio: ITermosRepository,
        aoRecusar: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            // Sem rede não dá pra confirmar: não trava a pessoa agora, pergunta
            // na próxima entrada.
            if (repositorio.aceitou(perfil).getOrDefault(true)) return@launch
            if (activity.isFinishing || activity.isDestroyed) return@launch
            mostrar(activity, perfil, repositorio, aoRecusar)
        }
    }

    private fun mostrar(
        activity: AppCompatActivity,
        perfil: String,
        repositorio: ITermosRepository,
        aoRecusar: () -> Unit
    ) {
        val densidade = activity.resources.displayMetrics.density
        val margem = (20 * densidade).toInt()
        val nomePerfil = when (perfil) {
            ITermosRepository.PERFIL_MOTORISTA -> "motorista"
            ITermosRepository.PERFIL_PRESTADOR -> "prestador"
            else -> "administração"
        }

        val texto = TextView(activity).apply {
            text = "Para usar o SOS Estrada como $nomePerfil, leia e aceite os Termos de uso e a Política de privacidade. " +
                "Cada perfil (motorista, prestador e administração) aceita separadamente, mesmo usando o mesmo e-mail."
            setTextColor(ContextCompat.getColor(activity, R.color.admin_texto))
            textSize = 15f
        }
        val link = TextView(activity).apply {
            text = "📄 Ler os Termos de uso e privacidade"
            setTextColor(ContextCompat.getColor(activity, R.color.admin_azul))
            textSize = 15f
            setPadding(0, margem / 2, 0, margem / 2)
            setOnClickListener {
                activity.startActivity(
                    TextoInformativoActivity.intent(activity, ConteudoSos.TERMOS, perfil == ITermosRepository.PERFIL_PRESTADOR)
                )
            }
        }
        val caixa = CheckBox(activity).apply {
            text = "Li e aceito os Termos de uso e a Política de privacidade"
        }
        val conteudo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margem, margem / 2, margem, 0)
            addView(texto)
            addView(link)
            addView(caixa)
        }

        val dialogo = AlertDialog.Builder(activity)
            .setTitle("Termos de uso e privacidade")
            .setView(conteudo)
            .setCancelable(false)
            .setPositiveButton("Aceitar", null)
            .setNegativeButton("Recusar e sair") { _, _ -> aoRecusar() }
            .create()
        dialogo.setOnShowListener {
            val aceitar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            aceitar.isEnabled = false
            caixa.setOnCheckedChangeListener { _, marcado -> aceitar.isEnabled = marcado }
            // Botão tratado à mão: o diálogo só fecha depois de gravar o aceite.
            aceitar.setOnClickListener {
                aceitar.isEnabled = false
                activity.lifecycleScope.launch {
                    repositorio.registrarAceite(perfil)
                        .onSuccess { dialogo.dismiss() }
                        .onFailure { e ->
                            aceitar.isEnabled = caixa.isChecked
                            Toast.makeText(activity, "Não foi possível registrar o aceite: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
        dialogo.show()
    }
}
