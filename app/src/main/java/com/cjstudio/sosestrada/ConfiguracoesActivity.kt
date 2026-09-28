package com.cjstudio.sosestrada

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cjstudio.sosestrada.databinding.ActivityConfiguracoesBinding

// Configurações (ícone ⚙️ no cabeçalho dos painéis): Termos de uso e
// privacidade (iguais pros dois), Regras (cada lado com as suas) e, só pro
// prestador, Assinatura. Cor do cabeçalho segue o painel de origem.
class ConfiguracoesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracoesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracoesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prestador = intent.getStringExtra(EXTRA_TIPO) == IChatRepository.PRESTADOR
        binding.cabecalhoConfiguracoes.setBackgroundResource(
            if (prestador) R.drawable.bg_prestador_header else R.drawable.bg_motorista_header
        )
        binding.raizConfiguracoes.setBackgroundColor(
            ContextCompat.getColor(this, if (prestador) R.color.prestador_fundo else R.color.motorista_fundo)
        )

        binding.tvTituloRegras.text = if (prestador) ConteudoSos.TITULO_REGRAS_PRESTADOR else ConteudoSos.TITULO_REGRAS_MOTORISTA
        binding.tvSubtituloRegras.text = if (prestador) "Como atender e o que é esperado de você" else "Passo a passo do pedido de socorro"
        binding.cardAssinatura.visibility = if (prestador) View.VISIBLE else View.GONE

        binding.btnVoltarConfiguracoes.setOnClickListener { finish() }
        binding.cardTermos.setOnClickListener {
            startActivity(TextoInformativoActivity.intent(this, ConteudoSos.TITULO_TERMOS, ConteudoSos.termos, prestador))
        }
        binding.cardRegras.setOnClickListener {
            startActivity(
                if (prestador) TextoInformativoActivity.intent(this, ConteudoSos.TITULO_REGRAS_PRESTADOR, ConteudoSos.regrasPrestador, true)
                else TextoInformativoActivity.intent(this, ConteudoSos.TITULO_REGRAS_MOTORISTA, ConteudoSos.regrasMotorista, false)
            )
        }
        binding.cardAssinatura.setOnClickListener {
            startActivity(Intent(this, AssinaturaActivity::class.java))
        }
    }

    companion object {
        private const val EXTRA_TIPO = "tipo"

        // tipo: IChatRepository.MOTORISTA ou IChatRepository.PRESTADOR
        fun intent(context: Context, tipo: String) =
            Intent(context, ConfiguracoesActivity::class.java).putExtra(EXTRA_TIPO, tipo)
    }
}
