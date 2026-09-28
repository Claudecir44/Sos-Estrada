package com.cjstudio.sosestrada

import android.content.Context
import android.util.Log
import me.leolin.shortcutbadger.ShortcutBadger

// Número em cima do ícone do SOS Estrada na tela inicial — o mesmo total dos
// alertas do painel (MotoristaDashboardActivity/PrestadorDashboardActivity).
// A API do Android só deixa o canal de notificação pedir um badge; o número
// em si é desenhado pelo launcher de cada fabricante, cada um com sua API,
// e o ShortcutBadger fala com cada um. Launcher sem suporte lança exceção —
// nunca derruba o app por isso.
object AppIconBadgeUtil {
    private const val TAG = "AppIconBadgeUtil"

    fun atualizar(context: Context, total: Int) {
        try {
            if (total > 0) ShortcutBadger.applyCount(context.applicationContext, total)
            else ShortcutBadger.removeCount(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Launcher não suporta badge de ícone: ${e.message}")
        }
    }
}
