package com.cjstudio.sosestrada

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Tela de assinatura do prestador: 180 dias grátis a partir do cadastro
// (aoRegistrarPrestador, Cloud Function), depois um plano anual de
// R$49,90/365 dias pra manter o cadastro visível na busca do motorista
// (SocorroActivity só lista prestadores com ativo==true). A confirmação de
// pagamento NUNCA vem desta tela sozinha — vem do webhook
// (paymentWebhookPrestador) escrevendo em Firestore; por isso a tela
// escuta o próprio documento do prestador em vez de assumir sucesso ao
// voltar do checkout (mesmo desenho do AssinaturaActivity do Match).
class AssinaturaActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDetalhe: TextView
    private lateinit var btnAssinar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutAguardando: View

    private lateinit var db: FirebaseFirestore
    private lateinit var functions: FirebaseFunctions
    private var prestadorId: String? = null

    // assinaturaExpiraEm de antes de abrir o checkout — usado pra saber se
    // o valor que chegar depois pelo listener é realmente uma confirmação
    // NOVA (e não só o estado antigo ainda sem mudar).
    private var expiraEmAntesDaCompra: Long = 0L
    private var listenerConfirmacao: ListenerRegistration? = null
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assinatura)

        db = FirebaseFirestore.getInstance()
        functions = FirebaseFunctions.getInstance()

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Faça login novamente", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        prestadorId = user.uid

        tvStatus = findViewById(R.id.tvStatusAssinatura)
        tvDetalhe = findViewById(R.id.tvDetalheAssinatura)
        btnAssinar = findViewById(R.id.btnAssinar)
        progressBar = findViewById(R.id.progressBarAssinatura)
        layoutAguardando = findViewById(R.id.layoutAguardandoConfirmacao)
        findViewById<View>(R.id.btnVoltarAssinatura).setOnClickListener { finish() }

        btnAssinar.setOnClickListener { iniciarPagamento() }

        carregarStatus()
    }

    private fun carregarStatus() {
        val id = prestadorId ?: return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val doc = db.collection("prestadores").document(id).get().await()
                progressBar.visibility = View.GONE
                exibirStatus(doc)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AssinaturaActivity, "Erro ao carregar assinatura: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exibirStatus(doc: DocumentSnapshot) {
        val status = doc.getString("assinaturaStatus") ?: "trial"
        val dataCadastro = doc.getTimestamp("dataCadastro")?.toDate()?.time
        val expiraEm = doc.getTimestamp("assinaturaExpiraEm")?.toDate()?.time
        expiraEmAntesDaCompra = expiraEm ?: 0L

        when (status) {
            "ativa" -> {
                tvStatus.text = "✅ Assinatura ativa"
                tvDetalhe.text = if (expiraEm != null) "Válida até ${sdf.format(Date(expiraEm))}" else ""
                btnAssinar.text = "Renovar plano anual (R$ 49,90)"
            }

            "expirada" -> {
                tvStatus.text = "⛔ Cadastro inativo"
                tvDetalhe.text = "Seu período grátis ou sua assinatura venceu. Seu cadastro não aparece mais para motoristas até renovar."
                btnAssinar.text = "Assinar plano anual (R$ 49,90)"
            }

            else -> { // trial
                val diasRestantes = dataCadastro?.let {
                    val diasPassados = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it)
                    (TRIAL_DIAS - diasPassados).coerceAtLeast(0)
                }
                tvStatus.text = "🎁 Período grátis"
                tvDetalhe.text = if (diasRestantes != null) {
                    "$diasRestantes dia(s) restantes de $TRIAL_DIAS dias grátis"
                } else {
                    "Aproveitando o período de testes"
                }
                btnAssinar.text = "Assinar plano anual (R$ 49,90)"
            }
        }
    }

    // Preço e duração são decididos só pelo PLANO_ANUAL no servidor
    // (criarPreferenciaPagamentoPrestador, functions/index.js) — o cliente
    // não manda valor nem dias, só chama a function.
    private fun iniciarPagamento() {
        progressBar.visibility = View.VISIBLE
        btnAssinar.isEnabled = false

        lifecycleScope.launch {
            try {
                val resultado = functions.getHttpsCallable("criarPreferenciaPagamentoPrestador")
                    .call()
                    .await()

                @Suppress("UNCHECKED_CAST")
                val resposta = resultado.data as? Map<String, Any?>
                val initPoint = resposta?.get("initPoint") as? String

                progressBar.visibility = View.GONE
                btnAssinar.isEnabled = true

                if (initPoint.isNullOrEmpty()) {
                    Toast.makeText(this@AssinaturaActivity, "Não foi possível iniciar o pagamento.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(initPoint)))
                iniciarEscutaDeConfirmacao()
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnAssinar.isEnabled = true
                Log.e(TAG, "Erro ao criar preferência de pagamento", e)
                Toast.makeText(this@AssinaturaActivity, "Erro ao iniciar pagamento: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Escuta o próprio documento em tempo real: quando assinaturaExpiraEm
    // mudar pra um valor novo e válido (diferente do que já era antes de
    // abrir o checkout), é porque o webhook confirmou o pagamento no
    // servidor. Continua funcionando mesmo que o usuário nunca volte pelo
    // deep link — só depende do Firestore, não do redirecionamento.
    private fun iniciarEscutaDeConfirmacao() {
        val id = prestadorId ?: return
        layoutAguardando.visibility = View.VISIBLE

        listenerConfirmacao?.remove()
        listenerConfirmacao = db.collection("prestadores").document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro ao escutar confirmação de pagamento", error)
                    return@addSnapshotListener
                }
                val expiraEm = snapshot?.getTimestamp("assinaturaExpiraEm")?.toDate()?.time ?: return@addSnapshotListener
                val agora = System.currentTimeMillis()
                if (expiraEm > agora && expiraEm != expiraEmAntesDaCompra) {
                    listenerConfirmacao?.remove()
                    listenerConfirmacao = null
                    layoutAguardando.visibility = View.GONE
                    Toast.makeText(this, "🎉 Assinatura ativada com sucesso!", Toast.LENGTH_LONG).show()
                    exibirStatus(snapshot)
                }
            }
    }

    // Retorno do checkout via deep link (sosestrada://payment_success/...,
    // ver AndroidManifest.xml + back_urls em
    // criarPreferenciaPagamentoPrestador). Só traz a tela de volta pro
    // topo — quem decide se o pagamento foi aprovado é sempre o listener
    // acima, nunca essa navegação em si.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerConfirmacao?.remove()
    }

    companion object {
        private const val TAG = "AssinaturaActivity"
        private const val TRIAL_DIAS = 180
    }
}
