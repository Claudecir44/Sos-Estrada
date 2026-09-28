package com.cjstudio.sosestrada

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Mesmo padrão do Caronas: o servidor manda só "data" (nunca um bloco
// "notification"), e é aqui que se decide título, canal e pra onde o toque
// leva, pelo campo "tipo" (functions/index.js):
// - mensagemSos        -> "Mensagem Sos Estrada"   (abre a lista do lado de quem recebeu)
// - novaSolicitacao    -> "Solicitação Sos Estrada" (prestador: Atender solicitações)
// - respostaPrestador  -> "Prestador respondeu"     (motorista: Preciso de socorro)
// - localizacaoMotorista -> "Localização Sos Estrada" (prestador: Atender solicitações)
@AndroidEntryPoint
class SosFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var notificacaoRepository: INotificacaoRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        notificacaoRepository.registrarToken(authRepository.uidLogado())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val dados = message.data
        val corpo = dados["corpo"] ?: return
        // "motorista" ou "prestador": qual lado recebe, pra abrir a tela certa.
        val paraPrestador = dados["destino"] == IChatRepository.PRESTADOR

        val (titulo, canalId, canalNome) = when (dados["tipo"]) {
            "mensagemSos" -> Triple("Mensagem Sos Estrada", "mensagens_sos", "Mensagens")
            "novaSolicitacao" -> Triple("Solicitação Sos Estrada", "solicitacoes_sos", "Novas solicitações")
            "respostaPrestador" -> Triple("Prestador respondeu", "respostas_sos", "Respostas do prestador")
            "localizacaoMotorista" -> Triple("Localização Sos Estrada", "localizacoes_sos", "Localização do motorista")
            else -> return
        }
        val idNotificacao = dados["id"]?.hashCode() ?: System.currentTimeMillis().toInt()
        mostrarNotificacao(canalId, canalNome, titulo, corpo, idNotificacao, paraPrestador)
    }

    private fun mostrarNotificacao(
        canalId: String,
        canalNome: String,
        titulo: String,
        corpo: String,
        idNotificacao: Int,
        paraPrestador: Boolean
    ) {
        // Painel embaixo e a lista em cima: o "voltar" cai no painel.
        val (painel, lista) = if (paraPrestador) {
            PrestadorDashboardActivity::class.java to AtendimentoActivity::class.java
        } else {
            MotoristaDashboardActivity::class.java to SocorroActivity::class.java
        }
        val pendingIntent = TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, painel))
            .addNextIntent(Intent(this, lista))
            .getPendingIntent(idNotificacao, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ?: return

        val som = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Configurações do canal só valem na primeira criação (depois é o
            // usuário quem manda, nas configurações do sistema).
            val canal = NotificationChannel(canalId, canalNome, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(som, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(canal)
        }

        val notificacao = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(0xFFD32F2F.toInt())
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            // Pra Android 7 e anteriores, que não têm canal.
            .setSound(som)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        manager.notify(idNotificacao, notificacao)
    }
}
