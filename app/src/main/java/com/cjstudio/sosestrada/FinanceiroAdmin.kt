package com.cjstudio.sosestrada

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.cjstudio.sosestrada.databinding.ItemPagamentoAdminBinding
import com.cjstudio.sosestrada.databinding.SecaoConfiguracoesAdminBinding
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Financeiro do app admin (⚙️ Configurações > Financeiro), aberto na mesma
// tela das listas. Mesmas opções do Financeiro do Caronas: filtro de período
// (todos, diário, semanal, mensal, trimestral, semestral, anual ou datas
// escolhidas), três totais (pagamentos, prestadores pagantes, total
// arrecadado), lista dos pagamentos e relatório mensal em PDF com os
// pagamentos do mês e o resumo por valor. Os dados são as assinaturas dos
// prestadores (coleção pagamentos — ver PagamentoPrestador).
class FinanceiroAdmin(
    private val activity: Activity,
    private val tela: SecaoConfiguracoesAdminBinding,
    private val adminRepository: IAdminRepository,
    private val escopo: LifecycleCoroutineScope
) {
    private val sdfData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    private val sdfDataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    private var pagamentos: List<PagamentoPrestador> = emptyList()
    private var configurado = false

    private val periodos = listOf(
        "Todos" to 0, "Diário (hoje)" to 1, "Semanal (7 dias)" to 7, "Mensal (30 dias)" to 30,
        "Trimestral (3 meses)" to 90, "Semestral (6 meses)" to 180, "Anual (12 meses)" to 365, "Personalizado" to -1
    )
    private val meses = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho",
        "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

    // Chamado ao abrir o Financeiro: prepara o filtro (uma vez) e busca os pagamentos.
    fun abrir() {
        if (!configurado) configurar()
        tela.progressFinanceiro.visibility = View.VISIBLE
        tela.tvVazioFinanceiro.visibility = View.GONE
        tela.containerPagamentos.removeAllViews()
        escopo.launch {
            adminRepository.listarPagamentos()
                .onSuccess {
                    pagamentos = it
                    aplicarFiltro()
                }
                .onFailure { e ->
                    tela.progressFinanceiro.visibility = View.GONE
                    tela.tvVazioFinanceiro.text = "Erro ao carregar o financeiro.\n${e.message}"
                    tela.tvVazioFinanceiro.visibility = View.VISIBLE
                }
        }
    }

    private fun configurar() {
        configurado = true
        tela.spinnerPeriodoFinanceiro.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, periodos.map { it.first })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        tela.spinnerPeriodoFinanceiro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, posicao: Int, id: Long) {
                tela.layoutDatasFinanceiro.visibility = if (periodos[posicao].second == -1) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                tela.layoutDatasFinanceiro.visibility = View.GONE
            }
        }
        mascaraData(tela.etDataInicialFinanceiro)
        mascaraData(tela.etDataFinalFinanceiro)
        tela.btnFiltrarFinanceiro.setOnClickListener { aplicarFiltro() }
        tela.btnGerarRelatorioFinanceiro.setOnClickListener { escolherMesDoRelatorio() }
    }

    private fun aplicarFiltro() {
        val dias = periodos[tela.spinnerPeriodoFinanceiro.selectedItemPosition].second
        val agora = System.currentTimeMillis()
        var inicio = 0L
        var fim = Long.MAX_VALUE

        if (dias == -1) {
            val de = tela.etDataInicialFinanceiro.text.toString().trim()
            val ate = tela.etDataFinalFinanceiro.text.toString().trim()
            if (de.isEmpty() || ate.isEmpty()) return aviso("Preencha as duas datas.")
            try {
                val dataDe = sdfData.parse(de) ?: return aviso("Data inválida. Use dd/mm/aaaa.")
                val dataAte = sdfData.parse(ate) ?: return aviso("Data inválida. Use dd/mm/aaaa.")
                if (dataDe.after(dataAte)) return aviso("A data inicial é depois da final.")
                inicio = dataDe.time
                fim = dataAte.time + 24L * 60 * 60 * 1000 - 1
            } catch (e: ParseException) {
                return aviso("Data inválida. Use dd/mm/aaaa.")
            }
        } else if (dias > 0) {
            inicio = agora - dias * 24L * 60 * 60 * 1000
        }

        val filtrados = pagamentos.filter { p -> p.dataCompra?.let { it in inicio..fim } ?: false }
        tela.tvTotalPagamentosFinanceiro.text = filtrados.size.toString()
        tela.tvPrestadoresPagantesFinanceiro.text = filtrados.mapNotNull { it.prestadorId?.ifEmpty { null } }.toSet().size.toString()
        tela.tvTotalArrecadadoFinanceiro.text = reais(filtrados.sumOf { it.valor })

        tela.progressFinanceiro.visibility = View.GONE
        tela.containerPagamentos.removeAllViews()
        tela.tvVazioFinanceiro.text = "Nenhum pagamento encontrado nesse período."
        tela.tvVazioFinanceiro.visibility = if (filtrados.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(activity)
        filtrados.sortedByDescending { it.dataCompra }.forEach { p ->
            val item = ItemPagamentoAdminBinding.inflate(inflater, tela.containerPagamentos, false)
            item.tvNomePagamento.text = p.prestadorNome?.ifEmpty { null } ?: "Prestador"
            item.tvValorPagamento.text = reais(p.valor)
            item.tvEmailPagamento.text = p.prestadorEmail?.ifEmpty { null } ?: "-"
            item.tvDatasPagamento.text = "Pago em ${data(p.dataCompra)} • vale até ${data(p.expiraEm)}"
            val ativo = (p.expiraEm ?: 0L) > agora
            item.tvStatusPagamento.text = if (ativo) "✅ Ativo" else "Expirado"
            item.tvStatusPagamento.setTextColor(if (ativo) 0xFF15803D.toInt() else 0xFFC62828.toInt())
            tela.containerPagamentos.addView(item.root)
        }
    }

    // ----- Relatório mensal em PDF (mesmo modelo do Caronas) -----

    private fun escolherMesDoRelatorio() {
        val hoje = Calendar.getInstance()
        val anoAtual = hoje.get(Calendar.YEAR)
        val anos = (anoAtual downTo anoAtual - 5).map { it.toString() }
        val spinnerMes = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, meses)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(hoje.get(Calendar.MONTH))
        }
        val spinnerAno = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, anos)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        val conteudo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(TextView(activity).apply { text = "Mês" })
            addView(spinnerMes)
            addView(TextView(activity).apply { text = "Ano"; setPadding(0, 24, 0, 0) })
            addView(spinnerAno)
        }
        AlertDialog.Builder(activity)
            .setTitle("📄 Relatório mensal")
            .setView(conteudo)
            .setPositiveButton("Gerar") { _, _ ->
                gerarRelatorioMensal(spinnerMes.selectedItemPosition, anos[spinnerAno.selectedItemPosition].toInt())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun gerarRelatorioMensal(mes: Int, ano: Int) {
        val inicio = Calendar.getInstance().apply { clear(); set(ano, mes, 1) }
        val fim = (inicio.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val linhas = pagamentos
            .filter { p -> (p.dataCompra ?: -1L) in inicio.timeInMillis until fim.timeInMillis && p.valor > 0.0 }
            .sortedBy { it.dataCompra }
        if (linhas.isEmpty()) return aviso("Nenhum pagamento em ${meses[mes]}/$ano.")

        val pdf = PdfBuilder(activity)
        pdf.titulo("Relatório Financeiro — Assinaturas de Prestadores")
        pdf.subtitulo("Período: ${meses[mes]}/$ano")
        pdf.secao("Pagamentos do período")
        pdf.tabela(
            cabecalhos = listOf("Prestador", "E-mail", "Valor", "Data", "Vale até"),
            linhas = linhas.map { p ->
                listOf(p.prestadorNome?.ifEmpty { null } ?: "Prestador", p.prestadorEmail ?: "-", reais(p.valor), data(p.dataCompra), data(p.expiraEm))
            },
            pesos = listOf(0.28f, 0.27f, 0.15f, 0.15f, 0.15f)
        )
        // Agrupado pelo valor pago (mesmo resumo do Caronas).
        pdf.secao("Resumo por valor")
        pdf.tabela(
            cabecalhos = listOf("Valor", "Qtd", "Total"),
            linhas = linhas.groupBy { it.valor }.toSortedMap().map { (valor, itens) ->
                listOf(reais(valor), itens.size.toString(), reais(valor * itens.size))
            },
            pesos = listOf(0.34f, 0.33f, 0.33f)
        )
        pdf.linhaDestaque("Total geral: ${reais(linhas.sumOf { it.valor })}")
        pdf.rodape("Gerado em ${sdfDataHora.format(Date())} — SOS Estrada")
        pdf.gerarEAbrir("relatorio_financeiro_${ano}_${mes + 1}")
    }

    // ----- Utilitários -----

    private fun reais(valor: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", valor)

    private fun data(millis: Long?) = millis?.let { sdfData.format(Date(it)) } ?: "-"

    private fun aviso(texto: String) {
        Toast.makeText(activity, texto, Toast.LENGTH_SHORT).show()
    }

    // dd/mm/aaaa enquanto digita.
    private fun mascaraData(campo: EditText) {
        campo.addTextChangedListener(object : TextWatcher {
            private var atualizando = false
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (atualizando) return
                val digitos = s.toString().filter { it.isDigit() }.take(8)
                val formatado = buildString {
                    digitos.forEachIndexed { i, c ->
                        if (i == 2 || i == 4) append('/')
                        append(c)
                    }
                }
                atualizando = true
                campo.setText(formatado)
                campo.setSelection(formatado.length)
                atualizando = false
            }
        })
    }
}
