package com.cjstudio.sosestrada

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

// Android 13+ exige pedir a permissão de notificação em tempo de execução —
// sem ela os pushes (mensagem, solicitação, resposta) não aparecem. Nas
// versões anteriores ela já vem concedida.
fun Activity.pedirPermissaoNotificacao(lancador: ActivityResultLauncher<String>) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val concedida = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (!concedida) lancador.launch(Manifest.permission.POST_NOTIFICATIONS)
}
