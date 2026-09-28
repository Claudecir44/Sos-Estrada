package com.cjstudio.sosestrada

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton

// Um token por conta (o último aparelho usado), sobrescrito a cada chamada —
// mesmo modelo do Caronas. Falha silenciosa (só Log.e).
@Singleton
class NotificacaoRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) : INotificacaoRepository {

    override fun registrarToken(uid: String?) {
        if (uid.isNullOrBlank()) return
        messaging.token
            .addOnSuccessListener { token ->
                db.collection("fcmTokens").document(uid)
                    .set(mapOf("token" to token, "atualizadoEm" to FieldValue.serverTimestamp()))
                    .addOnFailureListener { e -> Log.e(TAG, "Erro ao salvar token: ${e.message}") }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Erro ao obter token: ${e.message}") }
    }

    private companion object {
        const val TAG = "NotificacaoRepository"
    }
}
