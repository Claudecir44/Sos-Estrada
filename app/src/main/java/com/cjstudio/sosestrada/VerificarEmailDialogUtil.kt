package com.cjstudio.sosestrada

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

// Aviso grande e fixo explicando como confirmar o e-mail (passo a passo +
// onde procurar: caixa de entrada, spam, abas do Gmail). Substitui os
// Toasts curtos de antes, que sumiam (e cortavam o texto) antes de a
// pessoa ler — e muita gente não sabia que precisava validar o e-mail.
//
// Duas versões do mesmo aviso: logo depois do cadastro (mostrar) e no
// login de conta ainda não confirmada (mostrarLogin, com a situação do
// e-mail — reenviado agora ou já enviado há menos de 1 hora).
//
// Não fecha tocando fora nem no voltar: só pelo botão, que chama onOk.
object VerificarEmailDialogUtil {

    fun mostrar(activity: Activity, email: String, onOk: () -> Unit) {
        exibir(activity, email, situacao = null, onOk = onOk)
    }

    fun mostrarLogin(activity: Activity, email: String, situacao: String) {
        exibir(activity, email, situacao = situacao, onOk = {})
    }

    private fun exibir(activity: Activity, email: String, situacao: String?, onOk: () -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_verificar_email, null)
        view.background = activity.getDrawable(R.drawable.bg_dialog_branco)
        view.findViewById<TextView>(R.id.tvEmailVerificar).text = email
        val btnOk = view.findViewById<Button>(R.id.btnEntendiVerificar)

        if (situacao != null) {
            view.findViewById<TextView>(R.id.tvTituloVerificar).setText(R.string.verificar_email_login_titulo)
            view.findViewById<TextView>(R.id.tvSubtituloVerificar).setText(R.string.verificar_email_login_subtitulo)
            view.findViewById<TextView>(R.id.tvSituacaoVerificar).apply {
                text = situacao
                visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.tvEnviadoParaVerificar).setText(R.string.verificar_email_login_enviado_para)
            view.findViewById<TextView>(R.id.tvPasso4).text = activity.getText(R.string.verificar_email_login_passo4)
            btnOk.setText(R.string.verificar_email_login_botao)
        }

        val dialog = Dialog(activity)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        btnOk.setOnClickListener {
            dialog.dismiss()
            onOk()
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.75f)
        }
        dialog.show()
        // Depois do show (antes ele é sobrescrito pelo tamanho padrão):
        // quase a largura toda da tela; a altura acompanha o conteúdo (em
        // tela pequena o miolo rola e o botão continua visível).
        val largura = (activity.resources.displayMetrics.widthPixels * 0.94f).toInt()
        dialog.window?.setLayout(largura, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
