package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

// Menu de segurar o cartão de motorista/prestador no painel admin:
// Editar (com Salvar), Bloquear/Desbloquear e Excluir. As três pedem a senha
// do administrador master — quem confere são as regras do Firestore
// (acaoAdminAutorizada), não o app.
class AcoesCadastroAdmin(
    private val activity: AppCompatActivity,
    private val adminRepository: IAdminRepository,
    private val aoConcluir: () -> Unit
) {

    // Um campo do formulário de edição: rótulo, chave no Firestore e valor atual.
    private class Campo(val rotulo: String, val chave: String, val valor: String?, val tipo: Int = InputType.TYPE_CLASS_TEXT)

    fun abrir(motorista: Motorista) = abrirMenu(
        nome = motorista.nome ?: "Motorista",
        colecao = AdminRepository.COLECAO_MOTORISTAS,
        uid = motorista.uid,
        bloqueado = motorista.bloqueado,
        campos = listOf(
            Campo("Nome", "nome", motorista.nome, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS),
            Campo("Telefone", "telefone", motorista.telefone, InputType.TYPE_CLASS_PHONE),
            Campo("Veículo", "veiculo", motorista.veiculo, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS),
            Campo("Placa", "placa", motorista.placa, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS),
            Campo("Cor", "cor", motorista.cor, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        ),
        salvar = { uid, dados, senha -> adminRepository.editarMotorista(uid, dados, senha) }
    )

    fun abrir(prestador: Prestador) = abrirMenu(
        nome = prestador.nome ?: "Prestador",
        colecao = AdminRepository.COLECAO_PRESTADORES,
        uid = prestador.uid,
        bloqueado = prestador.bloqueado,
        campos = listOf(
            Campo("Nome", "nome", prestador.nome, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS),
            Campo("Telefone", "telefone", prestador.telefone, InputType.TYPE_CLASS_PHONE),
            Campo("Serviço", "servico", prestador.servico, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
            Campo("Preço", "preco", prestador.preco),
            Campo("CNPJ", "cnpj", prestador.cnpj, InputType.TYPE_CLASS_NUMBER)
        ),
        salvar = { uid, dados, senha -> adminRepository.editarPrestador(uid, dados, senha) }
    )

    private fun abrirMenu(
        nome: String,
        colecao: String,
        uid: String?,
        bloqueado: Boolean,
        campos: List<Campo>,
        salvar: suspend (String, Map<String, Any?>, String) -> Result<Unit>
    ) {
        if (uid.isNullOrEmpty()) {
            avisar("❌ Cadastro sem identificação — não dá pra alterar.")
            return
        }
        val opcoes = arrayOf("✏️ Editar", if (bloqueado) "✅ Desbloquear" else "🚫 Bloquear", "🗑️ Excluir")
        AlertDialog.Builder(activity)
            .setTitle(nome)
            .setItems(opcoes) { _, escolha ->
                when (escolha) {
                    0 -> editar(nome, uid, campos, salvar)
                    1 -> confirmarComSenha(
                        titulo = if (bloqueado) "✅ Desbloquear $nome?" else "🚫 Bloquear $nome?",
                        mensagem = if (bloqueado) "A pessoa volta a entrar e usar o app normalmente."
                        else "A pessoa não consegue mais entrar no app nem ser encontrada (prestador) ou pedir socorro (motorista).",
                        botao = if (bloqueado) "Desbloquear" else "Bloquear",
                        sucesso = if (bloqueado) "✅ $nome desbloqueado." else "🚫 $nome bloqueado."
                    ) { senha -> adminRepository.definirBloqueio(colecao, uid, !bloqueado, senha) }
                    2 -> confirmarComSenha(
                        titulo = "🗑️ Excluir $nome?",
                        mensagem = "O cadastro é apagado de vez e a pessoa perde o acesso ao app. Não dá pra desfazer.",
                        botao = "Excluir",
                        sucesso = "🗑️ Cadastro de $nome excluído."
                    ) { senha -> adminRepository.excluirCadastro(colecao, uid, senha) }
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    // Formulário com os dados atuais + senha master; Salvar grava só os
    // campos da lista (e-mail e foto ficam com a própria pessoa).
    private fun editar(
        nome: String,
        uid: String,
        campos: List<Campo>,
        salvar: suspend (String, Map<String, Any?>, String) -> Result<Unit>
    ) {
        val formulario = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val margem = dp(20)
            setPadding(margem, dp(8), margem, 0)
        }
        val entradas = campos.map { campo ->
            val edt = adicionarCampo(formulario, campo.rotulo, campo.tipo).apply { setText(campo.valor.orEmpty()) }
            if (campo.tipo == InputType.TYPE_CLASS_PHONE) TelefoneUtil.aplicarMascara(edt)
            campo to edt
        }
        val edtSenha = adicionarCampoSenha(formulario)

        val dialogo = AlertDialog.Builder(activity)
            .setTitle("✏️ Editar $nome")
            .setView(ScrollView(activity).apply { addView(formulario) })
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            val botao = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            botao.setOnClickListener {
                val (campoNome, edtNome) = entradas.first()
                if (edtNome.text.isNullOrBlank()) {
                    edtNome.error = "${campoNome.rotulo} obrigatório"
                    return@setOnClickListener
                }
                val senha = lerSenha(edtSenha) ?: return@setOnClickListener
                val dados = entradas.associate { (campo, edt) -> campo.chave to edt.text.toString().trim() }
                executar(dialogo, botao, edtSenha, "✅ Alterações salvas.") { salvar(uid, dados, senha) }
            }
        }
        dialogo.show()
    }

    private fun confirmarComSenha(
        titulo: String,
        mensagem: String,
        botao: String,
        sucesso: String,
        acao: suspend (String) -> Result<Unit>
    ) {
        val formulario = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val edtSenha = adicionarCampoSenha(formulario)
        val dialogo = AlertDialog.Builder(activity)
            .setTitle(titulo)
            .setMessage(mensagem)
            .setView(formulario)
            .setPositiveButton(botao, null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            val btn = dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val senha = lerSenha(edtSenha) ?: return@setOnClickListener
                executar(dialogo, btn, edtSenha, sucesso) { acao(senha) }
            }
        }
        dialogo.show()
    }

    // Roda a ação; senha errada mantém o diálogo aberto com o erro no campo.
    private fun executar(
        dialogo: AlertDialog,
        botao: View,
        edtSenha: EditText,
        sucesso: String,
        acao: suspend () -> Result<Unit>
    ) {
        botao.isEnabled = false
        activity.lifecycleScope.launch {
            acao()
                .onSuccess {
                    dialogo.dismiss()
                    avisar(sucesso)
                    aoConcluir()
                }
                .onFailure { e ->
                    botao.isEnabled = true
                    if (e is SenhaMasterIncorretaException) {
                        edtSenha.error = "Senha master incorreta"
                        edtSenha.requestFocus()
                    } else {
                        avisar("❌ Não foi possível concluir: ${e.message}")
                    }
                }
        }
    }

    private fun lerSenha(edtSenha: EditText): String? {
        val senha = edtSenha.text.toString()
        if (senha.isEmpty()) {
            edtSenha.error = "Digite a senha do administrador master"
            edtSenha.requestFocus()
            return null
        }
        return senha
    }

    private fun adicionarCampo(pai: LinearLayout, rotulo: String, tipo: Int): EditText {
        val layout = TextInputLayout(activity).apply { hint = rotulo }
        val edt = TextInputEditText(layout.context).apply { inputType = tipo }
        layout.addView(edt)
        pai.addView(layout)
        return edt
    }

    private fun adicionarCampoSenha(pai: LinearLayout): EditText {
        val layout = TextInputLayout(activity).apply {
            hint = "🔐 Senha do administrador master"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edt = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(edt)
        pai.addView(layout)
        return edt
    }

    private fun dp(valor: Int) = (valor * activity.resources.displayMetrics.density).toInt()

    private fun avisar(mensagem: String) = Toast.makeText(activity, mensagem, Toast.LENGTH_LONG).show()
}
