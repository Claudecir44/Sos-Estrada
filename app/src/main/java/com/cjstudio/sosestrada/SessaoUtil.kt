package com.cjstudio.sosestrada

import android.content.Context

// Lembra com qual perfil (motorista ou prestador) a pessoa entrou, pra
// reabrir o app direto no painel certo (MainActivity) em vez da tela de
// escolha + login. A sessão em si é a do Firebase Auth, que já fica salva
// no aparelho; só o botão "Sair" dos painéis encerra as duas coisas.
object SessaoUtil {
    private const val PREFS = "sos_estrada_sessao"
    private const val KEY_PERFIL = "perfil"

    // perfil: IChatRepository.MOTORISTA ou IChatRepository.PRESTADOR
    fun salvarPerfil(context: Context, perfil: String) {
        prefs(context).edit().putString(KEY_PERFIL, perfil).apply()
    }

    fun perfil(context: Context): String? = prefs(context).getString(KEY_PERFIL, null)

    fun limpar(context: Context) {
        prefs(context).edit().remove(KEY_PERFIL).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
