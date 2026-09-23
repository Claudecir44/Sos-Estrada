package com.cjstudio.sosestrada

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityAdminMensagensBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Conversas de todas as solicitações (admin); tocar abre o chat em modo
// somente leitura.
@AndroidEntryPoint
class AdminMensagensActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var binding: ActivityAdminMensagensBinding
    private val adapter = AdminConversaAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMensagensBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltarAdminMensagens.setOnClickListener { finish() }
        binding.rvAdminMensagens.layoutManager = LinearLayoutManager(this)
        binding.rvAdminMensagens.adapter = adapter

        binding.progressBarAdminMensagens.visibility = View.VISIBLE
        lifecycleScope.launch {
            adminRepository.listarSolicitacoes()
                .onSuccess { lista ->
                    adapter.atualizarLista(lista)
                    binding.tvSubtituloAdminMensagens.text = if (lista.size == 1) "1 conversa" else "${lista.size} conversas"
                    binding.tvEmptyAdminMensagens.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { e ->
                    Toast.makeText(this@AdminMensagensActivity, "Erro ao carregar conversas: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.tvEmptyAdminMensagens.visibility = View.VISIBLE
                }
            binding.progressBarAdminMensagens.visibility = View.GONE
        }
    }
}
