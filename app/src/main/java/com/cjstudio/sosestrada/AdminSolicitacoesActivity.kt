package com.cjstudio.sosestrada

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cjstudio.sosestrada.databinding.ActivityAdminSolicitacoesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Todas as solicitações (admin), com os dados do motorista e do prestador.
@AndroidEntryPoint
class AdminSolicitacoesActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var binding: ActivityAdminSolicitacoesBinding
    private val adapter = AdminSolicitacaoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSolicitacoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvAdminSolicitacoes.layoutManager = LinearLayoutManager(this)
        binding.rvAdminSolicitacoes.adapter = adapter

        binding.progressBarAdminSolicitacoes.visibility = View.VISIBLE
        lifecycleScope.launch {
            adminRepository.listarSolicitacoes()
                .onSuccess { lista ->
                    adapter.atualizarLista(lista)
                    binding.tvEmptyAdminSolicitacoes.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { e ->
                    Toast.makeText(this@AdminSolicitacoesActivity, "Erro ao carregar solicitações: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.tvEmptyAdminSolicitacoes.visibility = View.VISIBLE
                }
            binding.progressBarAdminSolicitacoes.visibility = View.GONE
        }
    }
}
