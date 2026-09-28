package com.cjstudio.sosestrada

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.cjstudio.sosestrada.databinding.ActivityTextoInformativoBinding

// Mostra um texto das Configurações (ConteudoSos): termos de uso e
// privacidade ou as regras do motorista/prestador.
class TextoInformativoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextoInformativoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextoInformativoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cabecalhoTexto.setBackgroundResource(
            if (intent.getBooleanExtra(EXTRA_PRESTADOR, false)) R.drawable.bg_prestador_header else R.drawable.bg_motorista_header
        )
        binding.tvTituloTexto.text = intent.getStringExtra(EXTRA_TITULO)
        binding.tvConteudoTexto.text = HtmlCompat.fromHtml(intent.getStringExtra(EXTRA_HTML).orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.btnVoltarTexto.setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_TITULO = "titulo"
        private const val EXTRA_HTML = "html"
        private const val EXTRA_PRESTADOR = "prestador"

        fun intent(context: Context, titulo: String, html: String, prestador: Boolean) =
            Intent(context, TextoInformativoActivity::class.java)
                .putExtra(EXTRA_TITULO, titulo)
                .putExtra(EXTRA_HTML, html)
                .putExtra(EXTRA_PRESTADOR, prestador)
    }
}
