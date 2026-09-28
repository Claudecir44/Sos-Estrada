package com.cjstudio.sosestrada

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Monta um PDF campo a campo com android.graphics.pdf.PdfDocument (sem
// biblioteca externa, sem WebView e sem passar pela caixa de diálogo de
// impressão do sistema) e abre com o app padrão do aparelho (visualizador
// de PDF, WhatsApp, e-mail, etc.) assim que termina — mesmo utilitário do
// Match/Caronas (ver PdfUtil.kt de lá), portado pro SOS Estrada (ver
// AdminActivity, seção Financeiro).
private const val PAGINA_LARGURA = 595 // A4 a 72dpi, em pontos
private const val PAGINA_ALTURA = 842
private const val MARGEM = 36f

class PdfBuilder(private val activity: Activity) {
    private val document = PdfDocument()
    private var numeroPagina = 1
    private var page: PdfDocument.Page = novaPagina()
    private var canvas: Canvas = page.canvas
    private var y = MARGEM

    private val larguraUtil = PAGINA_LARGURA - 2 * MARGEM

    private val paintTitulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#B71C1C".toColorInt()
        textSize = 18f
        isFakeBoldText = true
    }
    private val paintSubtitulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#666666".toColorInt()
        textSize = 11f
    }
    private val paintSecao = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#B71C1C".toColorInt()
        textSize = 13f
        isFakeBoldText = true
    }
    private val paintCabecalho = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#666666".toColorInt()
        textSize = 9f
        isFakeBoldText = true
    }
    private val paintCelula = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#222222".toColorInt()
        textSize = 9.5f
    }
    private val paintCelulaNegrito = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#222222".toColorInt()
        textSize = 10f
        isFakeBoldText = true
    }
    private val paintTexto = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#222222".toColorInt()
        textSize = 10.5f
    }
    private val paintRodape = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#999999".toColorInt()
        textSize = 8f
    }
    private val corLinhaDestaque = "#B71C1C".toColorInt()
    private val corLinhaSutil = "#EEEEEE".toColorInt()
    private val corLinhaCabecalho = "#CCCCCC".toColorInt()

    private fun novaPagina(): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(PAGINA_LARGURA, PAGINA_ALTURA, numeroPagina).create()
        return document.startPage(info)
    }

    private fun quebrarPagina() {
        document.finishPage(page)
        numeroPagina++
        page = novaPagina()
        canvas = page.canvas
        y = MARGEM
    }

    private fun garantirEspaco(altura: Float) {
        if (y + altura > PAGINA_ALTURA - MARGEM) quebrarPagina()
    }

    private fun linha(cor: Int, larguraTraco: Float = 1f) {
        canvas.drawLine(MARGEM, y, MARGEM + larguraUtil, y, Paint().apply { color = cor; strokeWidth = larguraTraco })
    }

    fun titulo(texto: String) {
        garantirEspaco(26f)
        canvas.drawText(texto, MARGEM, y + 16f, paintTitulo)
        y += 24f
    }

    fun subtitulo(texto: String) {
        garantirEspaco(18f)
        canvas.drawText(texto, MARGEM, y + 10f, paintSubtitulo)
        y += 20f
    }

    fun secao(texto: String) {
        garantirEspaco(26f)
        y += 8f
        canvas.drawText(texto, MARGEM, y + 11f, paintSecao)
        y += 15f
        linha(corLinhaDestaque, 1.5f)
        y += 10f
    }

    // Quebra o texto em linhas que cabem em [larguraMax], sem cortar
    // palavras no meio quando dá pra evitar.
    private fun quebrarLinhas(texto: String, paint: TextPaint, larguraMax: Float): List<String> {
        val linhas = mutableListOf<String>()
        for (paragrafo in texto.split("\n")) {
            if (paragrafo.isEmpty()) {
                linhas.add("")
                continue
            }
            var inicio = 0
            while (inicio < paragrafo.length) {
                val contagem = paint.breakText(paragrafo, inicio, paragrafo.length, true, larguraMax, null)
                if (contagem <= 0) break
                var fim = inicio + contagem
                if (fim < paragrafo.length && paragrafo[fim] != ' ') {
                    val espaco = paragrafo.lastIndexOf(' ', fim - 1)
                    if (espaco > inicio) fim = espaco
                }
                linhas.add(paragrafo.substring(inicio, fim).trim())
                inicio = fim
                while (inicio < paragrafo.length && paragrafo[inicio] == ' ') inicio++
            }
        }
        return linhas
    }

    fun paragrafo(texto: String, paint: TextPaint = paintTexto) {
        val alturaLinha = paint.textSize * 1.4f
        for (linhaTexto in quebrarLinhas(texto, paint, larguraUtil)) {
            garantirEspaco(alturaLinha)
            canvas.drawText(linhaTexto, MARGEM, y + paint.textSize, paint)
            y += alturaLinha
        }
    }

    fun chaveValor(rotulo: String, valor: String) {
        val alturaLinha = paintCelula.textSize * 1.8f
        val linhasValor = quebrarLinhas(valor, paintCelulaNegrito, larguraUtil * 0.62f)
        val alturaTotal = maxOf(1, linhasValor.size) * (paintCelula.textSize * 1.5f) + 6f
        garantirEspaco(alturaTotal)
        canvas.drawText(rotulo, MARGEM, y + paintCelula.textSize, paintCelula)
        var yValor = y
        for (linhaTexto in linhasValor) {
            canvas.drawText(linhaTexto, MARGEM + larguraUtil * 0.35f, yValor + paintCelula.textSize, paintCelulaNegrito)
            yValor += paintCelula.textSize * 1.5f
        }
        y += alturaTotal
        linha(corLinhaSutil)
    }

    // [pesos] soma proporcional da largura útil (ex.: [0.3f, 0.2f, 0.5f]).
    fun tabela(cabecalhos: List<String>, linhas: List<List<String>>, pesos: List<Float>) {
        val larguras = pesos.map { it * larguraUtil }
        val xColunas = mutableListOf<Float>()
        var acumulado = MARGEM
        for (largura in larguras) {
            xColunas.add(acumulado)
            acumulado += largura
        }

        fun desenharCabecalho() {
            garantirEspaco(20f)
            for (i in cabecalhos.indices) {
                canvas.drawText(cabecalhos[i], xColunas[i], y + 9f, paintCabecalho)
            }
            y += 13f
            linha(corLinhaCabecalho)
            y += 8f
        }

        desenharCabecalho()

        for (dadosLinha in linhas) {
            val celulasQuebradas = dadosLinha.mapIndexed { i, texto -> quebrarLinhas(texto, paintCelula, larguras[i] - 6f) }
            val maxLinhas = celulasQuebradas.maxOf { it.size.coerceAtLeast(1) }
            val alturaLinhaTexto = paintCelula.textSize * 1.4f
            val alturaTotal = maxLinhas * alturaLinhaTexto + 6f

            if (y + alturaTotal > PAGINA_ALTURA - MARGEM) {
                quebrarPagina()
                desenharCabecalho()
            }

            for (i in dadosLinha.indices) {
                var yCelula = y
                for (textoLinha in celulasQuebradas[i]) {
                    canvas.drawText(textoLinha, xColunas[i], yCelula + paintCelula.textSize, paintCelula)
                    yCelula += alturaLinhaTexto
                }
            }
            y += alturaTotal
            linha(corLinhaSutil)
        }
        y += 6f
    }

    // Linha em destaque, alinhada à direita e em negrito — usada pra
    // totais gerais no fim de uma tabela de resumo.
    fun linhaDestaque(texto: String) {
        val paint = TextPaint(paintCelulaNegrito).apply { textSize = 13f; color = corLinhaDestaque }
        garantirEspaco(paint.textSize * 1.6f)
        y += 4f
        canvas.drawText(texto, MARGEM + larguraUtil - paint.measureText(texto), y + paint.textSize, paint)
        y += paint.textSize * 1.6f
    }

    fun rodape(texto: String) {
        canvas.drawText(texto, MARGEM + larguraUtil - paintRodape.measureText(texto), PAGINA_ALTURA - MARGEM / 2, paintRodape)
    }

    fun gerarEAbrir(nomeArquivoBase: String) {
        document.finishPage(page)

        val pastaRelatorios = File(activity.cacheDir, "relatorios").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val nomeSanitizado = nomeArquivoBase.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val arquivo = File(pastaRelatorios, "${nomeSanitizado}_$timestamp.pdf")

        try {
            try {
                FileOutputStream(arquivo).use { document.writeTo(it) }
            } finally {
                document.close()
            }

            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", arquivo)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Abrir PDF com"))
        } catch (e: Exception) {
            // Cobre tanto falha ao escrever o arquivo quanto
            // ActivityNotFoundException (nenhum app instalado sabe abrir
            // PDF) — sem isso, as duas falhas passavam em silêncio.
            Toast.makeText(activity, "Erro ao gerar o PDF.", Toast.LENGTH_LONG).show()
        }
    }
}
