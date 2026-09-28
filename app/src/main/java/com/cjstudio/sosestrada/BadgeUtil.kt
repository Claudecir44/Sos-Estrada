package com.cjstudio.sosestrada

import android.view.View
import android.widget.TextView

// Bolinha vermelha de alertas (estilo BadgeNotificacao): mostra o número,
// "99+" acima disso, e some com 0.
object BadgeUtil {
    fun mostrar(badge: TextView, total: Int) {
        if (total <= 0) {
            badge.visibility = View.GONE
        } else {
            badge.text = if (total > 99) "99+" else total.toString()
            badge.visibility = View.VISIBLE
        }
    }
}
