package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// Diálogos de segurança, iguais aos do Caronas: "Denunciar ou bloquear"
// (aberto do chat), "Reclamações, Sugestões e Denúncias" e "Usuários
// bloqueados" (Configurações). Exigência da política do Google Play para
// apps com conteúdo gerado por usuários.
object SegurancaDialogUtil {

    const val ORIGEM_CHAT = "chat"

    private val MOTIVOS_DENUNCIA = listOf(
        "Ofensa, ameaça ou assédio",
        "Golpe, cobrança abusiva ou fraude",
        "Pedido falso ou de brincadeira",
        "Não compareceu / não atendeu",
        "Conteúdo impróprio no chat",
        "Outro motivo"
    )

    fun mostrarOpcoes(
        activity: AppCompatActivity,
        outroUid: String,
        outroNome: String?,
        origem: String,
        repositorio: ISegurancaRepository
    ) {
        val nome = outroNome?.takeIf { it.isNotBlank() } ?: "Usuário"
        activity.lifecycleScope.launch {
            val jaBloqueei = repositorio.bloqueei(outroUid).getOrDefault(false)
            AlertDialog.Builder(activity)
                .setTitle(nome)
                .setItems(arrayOf("🚩 Denunciar", if (jaBloqueei) "✅ Desbloquear" else "🚫 Bloquear")) { _, indice ->
                    when {
                        indice == 0 -> denunciar(activity, outroUid, nome, origem, jaBloqueei, repositorio)
                        jaBloqueei -> desbloquear(activity, outroUid, nome, repositorio) {}
                        else -> confirmarBloqueio(activity, outroUid, nome, repositorio)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun denunciar(
        activity: AppCompatActivity,
        outroUid: String,
        nome: String,
        origem: String,
        jaBloqueei: Boolean,
        repositorio: ISegurancaRepository
    ) {
        val densidade = activity.resources.displayMetrics.density
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * densidade).toInt(), (8 * densidade).toInt(), (20 * densidade).toInt(), 0)
        }
        layout.addView(TextView(activity).apply { text = "Qual o problema com $nome?" })
        val grupo = RadioGroup(activity)
        MOTIVOS_DENUNCIA.forEachIndexed { i, motivo -> grupo.addView(RadioButton(activity).apply { id = View.generateViewId(); text = motivo; tag = i }) }
        layout.addView(grupo)
        val descricao = EditText(activity).apply { hint = "Conte o que aconteceu (opcional)"; minLines = 2 }
        layout.addView(descricao)

        val dialogo = AlertDialog.Builder(activity)
            .setTitle("🚩 Denunciar")
            .setView(layout)
            .setPositiveButton("Enviar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            // Não fecha sem escolher o motivo.
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val motivo = grupo.findViewById<RadioButton>(grupo.checkedRadioButtonId)?.text?.toString()
                if (motivo == null) {
                    Toast.makeText(activity, "Escolha o motivo da denúncia.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialogo.dismiss()
                activity.lifecycleScope.launch {
                    repositorio.enviarManifestacao(
                        Manifestacao.DENUNCIA, descricao.text.toString(), motivo, outroUid, nome, origem
                    ).onSuccess {
                        Toast.makeText(activity, "Denúncia enviada. A equipe vai analisar.", Toast.LENGTH_LONG).show()
                        if (!jaBloqueei) confirmarBloqueio(activity, outroUid, nome, repositorio)
                    }.onFailure { e ->
                        Toast.makeText(activity, "Não foi possível enviar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialogo.show()
    }

    private fun confirmarBloqueio(activity: AppCompatActivity, outroUid: String, nome: String, repositorio: ISegurancaRepository) {
        AlertDialog.Builder(activity)
            .setTitle("🚫 Bloquear $nome?")
            .setMessage("Vocês deixam de se ver na busca, não podem mais pedir socorro um ao outro nem trocar mensagens. Dá pra desbloquear depois em Configurações › Usuários bloqueados.")
            .setPositiveButton("Bloquear") { _, _ ->
                activity.lifecycleScope.launch {
                    repositorio.bloquear(outroUid, nome)
                        .onSuccess { Toast.makeText(activity, "$nome foi bloqueado.", Toast.LENGTH_SHORT).show() }
                        .onFailure { e -> Toast.makeText(activity, "Não foi possível bloquear: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun desbloquear(activity: AppCompatActivity, outroUid: String, nome: String, repositorio: ISegurancaRepository, depois: () -> Unit) {
        activity.lifecycleScope.launch {
            repositorio.desbloquear(outroUid)
                .onSuccess {
                    Toast.makeText(activity, "$nome foi desbloqueado.", Toast.LENGTH_SHORT).show()
                    depois()
                }
                .onFailure { e -> Toast.makeText(activity, "Não foi possível desbloquear: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // Configurações › Usuários bloqueados: toca no nome pra desbloquear.
    fun mostrarBloqueados(activity: AppCompatActivity, repositorio: ISegurancaRepository) {
        activity.lifecycleScope.launch {
            val bloqueados = repositorio.meusBloqueios().getOrElse { e ->
                Toast.makeText(activity, "Não foi possível carregar: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (bloqueados.isEmpty()) {
                AlertDialog.Builder(activity)
                    .setTitle("🚫 Usuários bloqueados")
                    .setMessage("Você não bloqueou ninguém. Para bloquear alguém, abra o chat com a pessoa e toque em ⋮.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            val lista = bloqueados.entries.toList()
            AlertDialog.Builder(activity)
                .setTitle("🚫 Usuários bloqueados")
                .setItems(lista.map { "${it.value}  — toque para desbloquear" }.toTypedArray()) { _, i ->
                    val (uid, nome) = lista[i]
                    AlertDialog.Builder(activity)
                        .setTitle("Desbloquear $nome?")
                        .setPositiveButton("Desbloquear") { _, _ -> desbloquear(activity, uid, nome, repositorio) { mostrarBloqueados(activity, repositorio) } }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
                .setNegativeButton("Fechar", null)
                .show()
        }
    }

    // Configurações › Reclamações, Sugestões e Denúncias.
    fun mostrarFaleConosco(activity: AppCompatActivity, repositorio: ISegurancaRepository) {
        val densidade = activity.resources.displayMetrics.density
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * densidade).toInt(), (8 * densidade).toInt(), (20 * densidade).toInt(), 0)
        }
        val tipos = listOf(Manifestacao.RECLAMACAO, Manifestacao.SUGESTAO, Manifestacao.DENUNCIA)
        val grupo = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        tipos.forEach { tipo -> grupo.addView(RadioButton(activity).apply { id = View.generateViewId(); text = Manifestacao.rotuloTipo(tipo); tag = tipo }) }
        layout.addView(grupo)
        val mensagem = EditText(activity).apply { hint = "Escreva sua mensagem"; minLines = 3 }
        layout.addView(mensagem)
        layout.addView(TextView(activity).apply {
            text = "A resposta chega no e-mail da sua conta."
            textSize = 12f
        })

        val dialogo = AlertDialog.Builder(activity)
            .setTitle("📝 Reclamações, Sugestões e Denúncias")
            .setView(layout)
            .setPositiveButton("Enviar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val tipo = grupo.findViewById<RadioButton>(grupo.checkedRadioButtonId)?.tag as? String
                val texto = mensagem.text.toString().trim()
                when {
                    tipo == null -> Toast.makeText(activity, "Escolha o tipo.", Toast.LENGTH_SHORT).show()
                    texto.isEmpty() -> Toast.makeText(activity, "Escreva a mensagem.", Toast.LENGTH_SHORT).show()
                    else -> {
                        dialogo.dismiss()
                        activity.lifecycleScope.launch {
                            repositorio.enviarManifestacao(tipo, texto)
                                .onSuccess { Toast.makeText(activity, "Mensagem enviada! Responderemos pelo seu e-mail.", Toast.LENGTH_LONG).show() }
                                .onFailure { e -> Toast.makeText(activity, "Não foi possível enviar: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }
            }
        }
        dialogo.show()
    }
}
