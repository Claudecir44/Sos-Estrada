package com.cjstudio.sosestrada

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
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
// - assinatura         -> "Plano Sos Estrada"      (prestador: plano termina em 5/2 dias)
// - chatAdmin          -> "Chat Admin Sos Estrada" (app admin: abre o chip 💬 Chat Admin)
@AndroidEntryPoint
class SosFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var notificacaoRepository: INotificacaoRepository

    @Inject
    lateinit var chatAdminRepository: IChatAdminRepository

    // O app admin (pacote ".admin") guarda o token em admins/{uid}, separado
    // do app de motorista/prestador da mesma conta (fcmTokens/{uid}).
    private val ehAppAdmin get() = packageName.endsWith(".admin")

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (ehAppAdmin) chatAdminRepository.registrarTokenAdmin()
        else notificacaoRepository.registrarToken(authRepository.uidLogado())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val dados = message.data
        val corpo = dados["corpo"] ?: return
        // Só mostra o aviso da conta logada neste celular agora. Se o aparelho
        // ainda estiver registrado pra outra conta (quem trocou de conta sem
        // sair), o aviso dela não aparece aqui — nem o da própria ação.
        val destinatario = dados["destinatarioUid"]
        if (destinatario != null && destinatario != authRepository.uidLogado()) return
        // "motorista" ou "prestador": qual lado recebe, pra abrir a tela certa.
        val paraPrestador = dados["destino"] == IChatRepository.PRESTADOR

        if (dados["tipo"] == "chatAdmin") {
            // Só no app admin; nunca traz o texto da mensagem, só quem mandou.
            if (!ehAppAdmin) return
            mostrarNotificacao(
                "chat_admin_sos", "Chat Admin", "Chat Admin Sos Estrada", corpo,
                dados["conversaId"]?.hashCode() ?: System.currentTimeMillis().toInt(),
                destino = AdminActivity.intentChatAdmin(this)
            )
            return
        }

        val (titulo, canalId, canalNome) = when (dados["tipo"]) {
            "mensagemSos" -> Triple("Mensagem Sos Estrada", "mensagens_sos", "Mensagens")
            "novaSolicitacao" -> Triple("Solicitação Sos Estrada", "solicitacoes_sos", "Novas solicitações")
            "respostaPrestador" -> Triple("Prestador respondeu", "respostas_sos", "Respostas do prestador")
            "localizacaoMotorista" -> Triple("Localização Sos Estrada", "localizacoes_sos", "Localização do motorista")
            "assinatura" -> Triple("Plano Sos Estrada", "plano_sos", "Fim do plano")
            else -> return
        }
        val idNotificacao = dados["id"]?.hashCode() ?: System.currentTimeMillis().toInt()
        // Aviso do plano abre só o painel; os outros já abrem a lista.
        mostrarNotificacao(canalId, canalNome, titulo, corpo, idNotificacao, paraPrestador = paraPrestador, abrirLista = dados["tipo"] != "assinatura")
    }

    private fun mostrarNotificacao(
        canalId: String,
        canalNome: String,
        titulo: String,
        corpo: String,
        idNotificacao: Int,
        paraPrestador: Boolean = false,
        abrirLista: Boolean = false,
        destino: Intent? = null
    ) {
        // Abre o painel do lado certo já com a lista aberta embaixo do cartão
        // (a lista agora fica dentro do painel, não numa tela separada).
        val painel = if (paraPrestador) PrestadorDashboardActivity::class.java else MotoristaDashboardActivity::class.java
        val abrir = (destino ?: Intent(this, painel).putExtra(MotoristaDashboardActivity.EXTRA_ABRIR_LISTA, abrirLista))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, idNotificacao, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .setColor(if (ehAppAdmin) 0xFF1F2937.toInt() else 0xFFD32F2F.toInt())
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
