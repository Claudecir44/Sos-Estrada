package com.cjstudio.sosestrada

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.cjstudio.sosestrada.databinding.ActivityTextoInformativoBinding
import com.cjstudio.sosestrada.databinding.ItemSecaoTextoBinding

// Mostra um texto das Configurações (ConteudoSos): termos de uso e
// privacidade ou as regras do motorista/prestador, um cartão por seção,
// com letra maior e espaço entre os itens (antes era um bloco só, miúdo).
class TextoInformativoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextoInformativoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextoInformativoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prestador = intent.getBooleanExtra(EXTRA_PRESTADOR, false)
        val chave = intent.getStringExtra(EXTRA_CHAVE) ?: ConteudoSos.TERMOS

        binding.cabecalhoTexto.setBackgroundResource(if (prestador) R.drawable.bg_prestador_header else R.drawable.bg_motorista_header)
        binding.raizTexto.setBackgroundColor(ContextCompat.getColor(this, if (prestador) R.color.prestador_fundo else R.color.motorista_fundo))
        binding.tvTituloTexto.text = ConteudoSos.titulo(chave)
        binding.tvIntroTexto.text = ConteudoSos.introducao(chave)
        binding.btnVoltarTexto.setOnClickListener { finish() }

        val corTitulo = ContextCompat.getColor(this, if (prestador) R.color.prestador_verde_escuro else R.color.motorista_azul_escuro)
        val corIcone = ContextCompat.getColor(this, if (prestador) R.color.prestador_verde_claro else R.color.motorista_azul_claro)
        ConteudoSos.secoes(chave).forEach { secao ->
            val cartao = ItemSecaoTextoBinding.inflate(layoutInflater, binding.containerSecoes, false)
            cartao.tvIconeSecao.text = secao.icone
            cartao.tvIconeSecao.backgroundTintList = ColorStateList.valueOf(corIcone)
            cartao.tvTituloSecao.text = secao.titulo
            cartao.tvTituloSecao.setTextColor(corTitulo)
            secao.itens.forEach { item -> cartao.containerItens.addView(paragrafo(item)) }
            binding.containerSecoes.addView(cartao.root)
        }
    }

    // Um item = um parágrafo de letra 16sp com espaço entre linhas; itens
    // soltos ganham um "•" (os passos numerados já começam com o número).
    private fun paragrafo(item: String): TextView {
        val numerado = Regex("^<b>\\d+\\.").containsMatchIn(item)
        val html = if (numerado) item else "•&nbsp;&nbsp;$item"
        return TextView(this).apply {
            text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(this@TextoInformativoActivity, R.color.admin_texto))
            setLineSpacing(0f, 1.35f)
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() }
        }
    }

    companion object {
        private const val EXTRA_CHAVE = "chave"
        private const val EXTRA_PRESTADOR = "prestador"

        // chave: ConteudoSos.TERMOS, REGRAS_MOTORISTA ou REGRAS_PRESTADOR
        fun intent(context: Context, chave: String, prestador: Boolean) =
            Intent(context, TextoInformativoActivity::class.java)
                .putExtra(EXTRA_CHAVE, chave)
                .putExtra(EXTRA_PRESTADOR, prestador)
    }
}
