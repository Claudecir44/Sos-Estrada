package com.cjstudio.sosestrada

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cjstudio.sosestrada.databinding.ActivityCadastroPrestadorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cadastro novo (cria a conta + prestadores/{uid}) ou edição do cadastro do
// prestador logado (aberta por intentEdicao, carrega os dados do Firestore).
@AndroidEntryPoint
class CadastroPrestadorActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var prestadorRepository: IPrestadorRepository

    private lateinit var binding: ActivityCadastroPrestadorBinding
    private var editando = false
    private var imagemSelecionada: Uri? = null
    private var logoUrlAtual: String? = null

    private val galeriaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        val uri = resultado.data?.data
        if (resultado.resultCode == RESULT_OK && uri != null) {
            imagemSelecionada = uri
            Glide.with(this).load(uri).centerCrop().into(binding.ivLogo)
        } else {
            Toast.makeText(this, "Nenhuma imagem selecionada", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissaoGaleriaLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            abrirGaleria()
        } else {
            Toast.makeText(this, "Permissão negada! Não é possível selecionar imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroPrestadorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TelefoneUtil.aplicarMascara(binding.edtTelefone)
        binding.ivLogo.setOnClickListener { verificarPermissaoEAbrirGaleria() }
        binding.btnSelecionarLogo.setOnClickListener { verificarPermissaoEAbrirGaleria() }

        editando = intent.getBooleanExtra(EXTRA_EDITANDO, false)
        binding.btnCadastrar.text = if (editando) "ATUALIZAR CADASTRO" else "CADASTRAR"
        if (editando) {
            // O e-mail é o login da conta — mudar só no cadastro deixaria os
            // dois diferentes. A senha também não muda por aqui.
            binding.edtEmail.isEnabled = false
            binding.edtSenha.isEnabled = false
            carregarCadastro()
        }

        binding.btnCadastrar.setOnClickListener { realizarCadastro() }
    }

    private fun carregarCadastro() {
        lifecycleScope.launch {
            prestadorRepository.buscarMeuCadastro()
                .onSuccess { prestador ->
                    if (prestador == null) return@onSuccess
                    binding.edtNome.setText(prestador.nome)
                    binding.edtCnpj.setText(prestador.cnpj)
                    binding.edtTelefone.setText(prestador.telefone)
                    binding.edtEmail.setText(prestador.email)
                    binding.edtServico.setText(prestador.servico)
                    binding.edtPreco.setText(prestador.preco)
                    binding.edtRua.setText(prestador.rua)
                    binding.edtNumero.setText(prestador.numero)
                    binding.edtBairro.setText(prestador.bairro)
                    binding.edtCidade.setText(prestador.cidade)
                    binding.edtComplemento.setText(prestador.complemento)
                    binding.edtEstado.setText(prestador.estado)
                    binding.edtPais.setText(prestador.pais)
                    logoUrlAtual = prestador.logo
                    // Só mostra a logo salva se o prestador ainda não escolheu outra.
                    if (!prestador.logo.isNullOrEmpty() && imagemSelecionada == null) {
                        Glide.with(this@CadastroPrestadorActivity)
                            .load(prestador.logo)
                            .centerCrop()
                            .placeholder(R.drawable.ic_placeholder_logo)
                            .into(binding.ivLogo)
                    }
                }
                .onFailure { e ->
                    Toast.makeText(this@CadastroPrestadorActivity, "Erro ao buscar dados: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun verificarPermissaoEAbrirGaleria() {
        val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED) {
            abrirGaleria()
        } else {
            permissaoGaleriaLauncher.launch(permissao)
        }
    }

    private fun abrirGaleria() {
        galeriaLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
    }

    private fun realizarCadastro() {
        val nome = binding.edtNome.textoLimpo()
        val cnpj = binding.edtCnpj.textoLimpo()
        val telefone = binding.edtTelefone.textoLimpo()
        val email = binding.edtEmail.textoLimpo()
        val senha = binding.edtSenha.textoLimpo()
        val servico = binding.edtServico.textoLimpo()
        val preco = binding.edtPreco.textoLimpo()
        val rua = binding.edtRua.textoLimpo()
        val numero = binding.edtNumero.textoLimpo()
        val bairro = binding.edtBairro.textoLimpo()
        val cidade = binding.edtCidade.textoLimpo()
        val complemento = binding.edtComplemento.textoLimpo()
        val estado = binding.edtEstado.textoLimpo().uppercase()
        val pais = binding.edtPais.textoLimpo()

        // Endereço: tudo obrigatório, exceto complemento.
        when {
            nome.isEmpty() -> return binding.edtNome.erro("Nome obrigatório")
            telefone.isEmpty() -> return binding.edtTelefone.erro("Telefone obrigatório")
            email.isEmpty() -> return binding.edtEmail.erro("E-mail obrigatório")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return binding.edtEmail.erro("E-mail inválido")
            !editando && SenhaUtil.validar(senha) != null -> return binding.edtSenha.erro(SenhaUtil.validar(senha)!!)
            servico.isEmpty() -> return binding.edtServico.erro("Tipo de serviço obrigatório")
            rua.isEmpty() -> return binding.edtRua.erro("Rua obrigatória")
            numero.isEmpty() -> return binding.edtNumero.erro("Número obrigatório")
            bairro.isEmpty() -> return binding.edtBairro.erro("Bairro obrigatório")
            cidade.isEmpty() -> return binding.edtCidade.erro("Cidade obrigatória")
            estado.isEmpty() -> return binding.edtEstado.erro("Estado obrigatório")
            pais.isEmpty() -> return binding.edtPais.erro("País obrigatório")
        }

        val prestador = Prestador(
            nome = nome, cnpj = cnpj, telefone = telefone, email = email, servico = servico, preco = preco,
            rua = rua, numero = numero, bairro = bairro, cidade = cidade, complemento = complemento,
            estado = estado, pais = pais
        )

        binding.btnCadastrar.isEnabled = false
        lifecycleScope.launch {
            // Edição: o prestador já está logado. Cadastro novo: cria a conta antes.
            if (!editando) {
                val conta = authRepository.criarConta(email, senha)
                if (conta.isFailure) {
                    binding.btnCadastrar.isEnabled = true
                    Toast.makeText(this@CadastroPrestadorActivity, "Erro ao criar conta: ${conta.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }

            // Logo nova é opcional: se o upload falhar, salva o resto e mantém a anterior.
            var logo = logoUrlAtual
            imagemSelecionada?.let { imagem ->
                prestadorRepository.enviarLogo(imagem)
                    .onSuccess { url -> logo = url }
                    .onFailure { e ->
                        Toast.makeText(this@CadastroPrestadorActivity, "Erro no upload: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            prestador.logo = logo

            prestadorRepository.salvarMeuCadastro(prestador, cadastroNovo = !editando)
                .onSuccess {
                    if (editando) {
                        Toast.makeText(this@CadastroPrestadorActivity, "✅ Dados atualizados!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        concluirCadastroNovo(email)
                    }
                }
                .onFailure { e ->
                    binding.btnCadastrar.isEnabled = true
                    Toast.makeText(this@CadastroPrestadorActivity, "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Conta nova só entra depois de validar o e-mail: manda a verificação,
    // encerra a sessão e volta pro login com o aviso.
    private suspend fun concluirCadastroNovo(email: String) {
        authRepository.enviarVerificacaoEmail()
        authRepository.sair()
        AlertDialog.Builder(this)
            .setTitle("✅ Cadastro realizado!")
            .setMessage("Enviamos um e-mail de verificação para $email.\n\nAbra o link do e-mail (confira também o spam) e depois entre com seu e-mail e senha.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                startActivity(Intent(this, LoginPrestadorActivity::class.java))
                finish()
            }
            .show()
    }

    private fun EditText.textoLimpo() = text.toString().trim()

    private fun EditText.erro(mensagem: String) {
        error = mensagem
        requestFocus()
    }

    companion object {
        private const val EXTRA_EDITANDO = "prestador"

        fun intentEdicao(context: Context): Intent =
            Intent(context, CadastroPrestadorActivity::class.java).putExtra(EXTRA_EDITANDO, true)
    }
}
