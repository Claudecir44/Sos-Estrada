package com.cjstudio.sosestrada

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Date
import java.util.Locale

// Migrada de Java pra Kotlin (era o arquivo com mais retorno de Firebase
// encadeado do app — 17 addOnSuccessListener/addOnFailureListener/
// addOnCompleteListener aninhados). Corrotinas (lifecycleScope + .await())
// no lugar dos callbacks: mesmo comportamento, código sequencial mais fácil
// de acompanhar e sem esquecer caso de erro por engano.
class SocorroActivity : AppCompatActivity(),
    PrestadorAdapter.OnSolicitarServicoListener,
    PrestadorAdapter.OnItemLongClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var edtPesquisa: TextInputEditText
    private lateinit var db: FirebaseFirestore
    private lateinit var mAuth: FirebaseAuth
    private lateinit var adapter: PrestadorAdapter
    private val prestadorList = mutableListOf<Prestador>()

    private var driverLat = 0.0
    private var driverLng = 0.0
    private var locationReady = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_socorro)

        db = FirebaseFirestore.getInstance()
        mAuth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        recyclerView = findViewById(R.id.rvPrestadoresSocorro)
        progressBar = findViewById(R.id.progressBarSocorro)
        tvEmpty = findViewById(R.id.tvEmptySocorro)
        edtPesquisa = findViewById(R.id.edtPesquisa)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PrestadorAdapter(prestadorList, this, this, this)
        recyclerView.adapter = adapter

        edtPesquisa.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        verificarPermissaoLocalizacao()
    }

    override fun onResume() {
        super.onResume()
        if (locationReady) {
            lifecycleScope.launch { carregarPrestadores() }
        }
    }

    private fun verificarPermissaoLocalizacao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST
            )
        } else {
            obterLocalizacaoAtual()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                obterLocalizacaoAtual()
            } else {
                Toast.makeText(this, "Permissão de localização negada. A distância não será calculada.", Toast.LENGTH_LONG).show()
                lifecycleScope.launch { carregarPrestadores() }
            }
        }
    }

    private fun obterLocalizacaoAtual() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        lifecycleScope.launch {
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    driverLat = location.latitude
                    driverLng = location.longitude
                    locationReady = true
                    Log.d("Socorro", "Localização obtida: $driverLat, $driverLng")
                } else {
                    Toast.makeText(this@SocorroActivity, "Não foi possível obter a localização. Verifique o GPS.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SocorroActivity, "Erro ao obter localização: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            carregarPrestadores()
        }
    }

    private suspend fun carregarPrestadores() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        try {
            // "ativo" só existe em prestadores criados depois do módulo de
            // assinatura (aoRegistrarPrestador, Cloud Function) — cadastros
            // antigos só recebem esse campo através da migração
            // (migrarAssinaturaPrestadores). Enquanto essa function não
            // rodar em produção (bloqueada pelo plano Spark), esse filtro
            // escondia todo mundo; rodar a migração é pré-requisito pra
            // essa tela voltar a listar prestadores antigos.
            val snapshot = db.collection("prestadores")
                .whereEqualTo("ativo", true)
                .get()
                .await()

            val tempList = snapshot.documents.mapNotNull { it.toObject(Prestador::class.java) }

            progressBar.visibility = View.GONE

            if (tempList.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                adapter.updateList(emptyList())
                return
            }

            val listaFinal = if (locationReady) calcularDistancias(tempList) else tempList
            carregarStatusSolicitacoes(listaFinal)
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Erro ao carregar prestadores: ${e.message}", Toast.LENGTH_SHORT).show()
            tvEmpty.visibility = View.VISIBLE
        }
    }

    private suspend fun calcularDistancias(lista: List<Prestador>): List<Prestador> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val geocoder = Geocoder(applicationContext, Locale.getDefault())
        lista.map { p ->
            try {
                var endereco = p.enderecoCompleto
                if (!endereco.isNullOrEmpty() && endereco != " " && endereco != "Endereço não informado"
                    && driverLat != 0.0 && driverLng != 0.0
                ) {
                    if (!endereco.lowercase().contains("brasil")) {
                        endereco += ", Brasil"
                    }
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(endereco, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val results = FloatArray(1)
                        Location.distanceBetween(driverLat, driverLng, address.latitude, address.longitude, results)
                        p.distancia = (results[0] / 1000).toDouble()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            p
        }
    }

    private suspend fun carregarStatusSolicitacoes(tempList: List<Prestador>) {
        val user = mAuth.currentUser
        if (user == null) {
            adapter.updateList(tempList)
            return
        }

        try {
            val snapshot = db.collection("solicitacoes")
                .whereEqualTo("motoristaUid", user.uid)
                .get()
                .await()

            val statusMap = HashMap<String, String>()
            val idMap = HashMap<String, String>()
            val naoLidasMap = HashMap<String, Long>()
            for (doc in snapshot.documents) {
                val prestadorUid = doc.getString("prestadorUid")
                val status = doc.getString("status")
                if (prestadorUid != null && status != null) {
                    statusMap[prestadorUid] = status
                    idMap[prestadorUid] = doc.id
                    naoLidasMap[prestadorUid] = doc.getLong("naoLidasMotorista") ?: 0L
                }
            }
            for (p in tempList) {
                p.statusSolicitacao = statusMap[p.uid]
                p.solicitacaoId = idMap[p.uid]
                p.naoLidasMotorista = (naoLidasMap[p.uid] ?: 0L).toInt()
            }
            prestadorList.clear()
            prestadorList.addAll(tempList)
            adapter.updateList(prestadorList)
        } catch (e: Exception) {
            adapter.updateList(tempList)
        }
    }

    // ---------- OnSolicitarServicoListener ----------
    override fun onSolicitarServico(prestador: Prestador) {
        val user = mAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "Faça login como motorista primeiro", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val existentes = db.collection("solicitacoes")
                    .whereEqualTo("motoristaUid", user.uid)
                    .whereEqualTo("prestadorUid", prestador.uid)
                    .whereIn("status", listOf("pendente", "aceito"))
                    .get()
                    .await()

                if (!existentes.isEmpty) {
                    Toast.makeText(this@SocorroActivity, "Você já possui uma solicitação pendente ou aceita para este prestador.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val motoristaDoc = db.collection("motoristas").document(user.uid).get().await()
                if (!motoristaDoc.exists()) {
                    Toast.makeText(this@SocorroActivity, "Dados do motorista não encontrados.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val solicitacao = hashMapOf<String, Any?>(
                    "motoristaUid" to user.uid,
                    "prestadorUid" to prestador.uid,
                    "prestadorNome" to prestador.nome,
                    "motoristaNome" to motoristaDoc.getString("nome"),
                    "motoristaTelefone" to motoristaDoc.getString("telefone"),
                    "motoristaVeiculo" to motoristaDoc.getString("veiculo"),
                    "motoristaPlaca" to motoristaDoc.getString("placa"),
                    "status" to "pendente",
                    "timestamp" to Date(),
                    "latitudeMotorista" to driverLat,
                    "longitudeMotorista" to driverLng
                )

                val enderecoMotorista = obterEnderecoAtual(driverLat, driverLng)
                if (enderecoMotorista != null) {
                    solicitacao["enderecoMotorista"] = enderecoMotorista
                }

                db.collection("solicitacoes").add(solicitacao).await()
                Toast.makeText(this@SocorroActivity, "✅ Solicitação enviada para ${prestador.nome}", Toast.LENGTH_LONG).show()
                prestador.statusSolicitacao = "pendente"
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(this@SocorroActivity, "Erro ao enviar solicitação: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- OnItemLongClickListener ----------
    override fun onItemLongClick(prestador: Prestador) {
        if (prestador.statusSolicitacao == null) {
            Toast.makeText(this, "Não há solicitação ativa para este prestador.", Toast.LENGTH_SHORT).show()
            return
        }

        if (prestador.statusSolicitacao == "cancelado") {
            AlertDialog.Builder(this)
                .setTitle("Excluir permanentemente")
                .setMessage("Esta solicitação já foi cancelada. Deseja excluí-la permanentemente?")
                .setPositiveButton("Sim") { _, _ -> excluirPermanentemente(prestador) }
                .setNegativeButton("Não", null)
                .show()
            return
        }

        mostrarDialogoCancelamento(prestador)
    }

    private fun mostrarDialogoCancelamento(prestador: Prestador) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_cancelar_solicitacao, null)
        val checkBox = view.findViewById<CheckBox>(R.id.checkBoxConfirmacaoCancelamento)
        val edtSenha = view.findViewById<EditText>(R.id.edtSenhaCancelamento)

        builder.setView(view)
        builder.setTitle("Cancelar solicitação")

        builder.setPositiveButton("Cancelar solicitação") { _, _ ->
            if (!checkBox.isChecked) {
                Toast.makeText(this, "Marque a checkbox para confirmar.", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            val senha = edtSenha.text.toString().trim()
            if (TextUtils.isEmpty(senha)) {
                Toast.makeText(this, "Digite sua senha.", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            reautenticarECancelar(prestador, senha)
        }

        builder.setNegativeButton("Voltar", null)
        builder.show()
    }

    private fun reautenticarECancelar(prestador: Prestador, senha: String) {
        val user = mAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "Usuário não logado", Toast.LENGTH_SHORT).show()
            return
        }
        val email = user.email
        if (email == null) {
            Toast.makeText(this, "E-mail não disponível", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                mAuth.signInWithEmailAndPassword(email, senha).await()
                cancelarSolicitacao(prestador)
            } catch (e: Exception) {
                Toast.makeText(this@SocorroActivity, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cancelarSolicitacao(prestador: Prestador) {
        val user = mAuth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val snapshot = db.collection("solicitacoes")
                    .whereEqualTo("motoristaUid", user.uid)
                    .whereEqualTo("prestadorUid", prestador.uid)
                    .get()
                    .await()

                if (snapshot.isEmpty) {
                    Toast.makeText(this@SocorroActivity, "Nenhuma solicitação encontrada.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                for (doc in snapshot.documents) {
                    try {
                        doc.reference.update("status", "cancelado").await()
                        Toast.makeText(this@SocorroActivity, "Solicitação cancelada com sucesso.", Toast.LENGTH_SHORT).show()
                        prestador.statusSolicitacao = "cancelado"
                        adapter.notifyDataSetChanged()
                    } catch (e: Exception) {
                        Toast.makeText(this@SocorroActivity, "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SocorroActivity, "Erro ao cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun excluirPermanentemente(prestador: Prestador) {
        val user = mAuth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val snapshot = db.collection("solicitacoes")
                    .whereEqualTo("motoristaUid", user.uid)
                    .whereEqualTo("prestadorUid", prestador.uid)
                    .get()
                    .await()

                if (snapshot.isEmpty) {
                    Toast.makeText(this@SocorroActivity, "Nenhuma solicitação encontrada.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                for (doc in snapshot.documents) {
                    try {
                        doc.reference.delete().await()
                        Toast.makeText(this@SocorroActivity, "Solicitação excluída permanentemente.", Toast.LENGTH_SHORT).show()
                        prestador.statusSolicitacao = null
                        adapter.notifyDataSetChanged()
                    } catch (e: Exception) {
                        Toast.makeText(this@SocorroActivity, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@SocorroActivity, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun obterEnderecoAtual(lat: Double, lng: Double): String? {
        @Suppress("DEPRECATION")
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) else null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}
