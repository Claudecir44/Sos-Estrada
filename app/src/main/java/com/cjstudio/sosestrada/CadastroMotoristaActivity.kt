package com.cjstudio.sosestrada

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cjstudio.sosestrada.databinding.ActivityCadastroMotoristaBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Cadastro novo (cria a conta + motoristas/{uid}, foto obrigatória) ou edição
// do cadastro do motorista logado (intentEdicao): abre com tudo preenchido,
// foto opcional (fica a salva) e e-mail editável — trocar o e-mail pede a
// senha e manda um link de confirmação pro e-mail novo.
@AndroidEntryPoint
class CadastroMotoristaActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: IAuthRepository

    @Inject
    lateinit var motoristaRepository: IMotoristaRepository

    private lateinit var binding: ActivityCadastroMotoristaBinding
    private var editando = false
    private var fotoNova: Uri? = null
    private var temFotoSalva = false

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            fotoNova = uri
            binding.ivFotoMotorista.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroMotoristaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editando = intent.getBooleanExtra(EXTRA_EDITANDO, false)
        TelefoneUtil.aplicarMascara(binding.edtTelefone)
        val abrirSeletor = View.OnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.ivFotoMotorista.setOnClickListener(abrirSeletor)
        binding.tvSelecionarFotoMotorista.setOnClickListener(abrirSeletor)

        if (editando) {
            binding.tvTituloCadastro.text = "Meu Perfil"
            binding.tvSelecionarFotoMotorista.text = "📷 Trocar foto"
            binding.btnCadastrar.text = "SALVAR ALTERAÇÕES"
            // A senha não muda por aqui ("Esqueci minha senha" no login).
            binding.tilSenha.visibility = View.GONE
            carregarCadastro()
        } else {
            binding.btnCadastrar.text = "CADASTRAR"
        }

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnCadastrar.setOnClickListener { realizarCadastro() }
    }

    private fun carregarCadastro() {
        // O e-mail do login vem na hora, mesmo antes do cadastro carregar.
        binding.edtEmail.setText(authRepository.emailLogado())
        lifecycleScope.launch {
            motoristaRepository.buscarMeuCadastro()
                .onSuccess { motorista ->
                    if (motorista == null) return@onSuccess
                    binding.edtNome.setText(motorista.nome)
                    binding.edtTelefone.setText(motorista.telefone)
                    binding.edtVeiculo.setText(motorista.veiculo)
                    binding.edtPlaca.setText(motorista.placa)
                    binding.edtCor.setText(motorista.cor)
                    temFotoSalva = !motorista.foto.isNullOrEmpty()
                    if (fotoNova == null) FotoUtil.mostrar(binding.ivFotoMotorista, motorista.foto)
                }
                .onFailure { e ->
                    Toast.makeText(this@CadastroMotoristaActivity, "Erro ao buscar dados: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun realizarCadastro() {
        val nome = binding.edtNome.textoLimpo()
        val telefone = binding.edtTelefone.textoLimpo()
        val email = binding.edtEmail.textoLimpo()
        val senha = binding.edtSenha.textoLimpo()
        val veiculo = binding.edtVeiculo.textoLimpo()
        val placa = binding.edtPlaca.textoLimpo().uppercase()
        val cor = binding.edtCor.textoLimpo()

        // Obrigatória só no cadastro novo; na edição de um cadastro antigo
        // sem foto, dá pra salvar o resto e escolher a foto depois.
        if (!editando && fotoNova == null && !temFotoSalva) {
            Toast.makeText(this, "Selecione uma foto.", Toast.LENGTH_SHORT).show()
            return
        }
        when {
            nome.isEmpty() -> return binding.edtNome.erro("Nome obrigatório")
            telefone.isEmpty() -> return binding.edtTelefone.erro("Telefone obrigatório")
            email.isEmpty() -> return binding.edtEmail.erro("E-mail obrigatório")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return binding.edtEmail.erro("E-mail inválido")
            !editando && SenhaUtil.validar(senha) != null -> return binding.edtSenha.erro(SenhaUtil.validar(senha)!!)
            veiculo.isEmpty() -> return binding.edtVeiculo.erro("Modelo do veículo obrigatório")
            placa.isEmpty() -> return binding.edtPlaca.erro("Placa obrigatória")
            cor.isEmpty() -> return binding.edtCor.erro("Cor do veículo obrigatória")
        }

        val emailDoLogin = authRepository.emailLogado()
        val trocouEmail = editando && !email.equals(emailDoLogin, ignoreCase = true)
        if (trocouEmail) {
            pedirSenhaParaTrocarEmail { senhaAtual -> salvar(nome, telefone, email, senha, veiculo, placa, cor, senhaAtual) }
        } else {
            salvar(nome, telefone, email, senha, veiculo, placa, cor, null)
        }
    }

    // senhaParaTrocarEmail != null: a edição também troca o e-mail de login.
    private fun salvar(
        nome: String, telefone: String, email: String, senha: String,
        veiculo: String, placa: String, cor: String, senhaParaTrocarEmail: String?
    ) {
        binding.btnCadastrar.isEnabled = false
        lifecycleScope.launch {
            if (!editando) {
                val conta = authRepository.criarConta(email, senha)
                if (conta.isFailure) {
                    binding.btnCadastrar.isEnabled = true
                    Toast.makeText(this@CadastroMotoristaActivity, "Erro ao criar conta: ${conta.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
            if (senhaParaTrocarEmail != null) {
                val troca = authRepository.trocarEmail(email, senhaParaTrocarEmail)
                if (troca.isFailure) {
                    binding.btnCadastrar.isEnabled = true
                    Toast.makeText(this@CadastroMotoristaActivity, "Não foi possível trocar o e-mail: ${troca.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }

            val foto = fotoNova?.let { FotoUtil.paraBase64(this@CadastroMotoristaActivity, it) }
            // Até o link ser confirmado o login continua sendo o e-mail antigo,
            // então o cadastro também (sincronizarEmail acerta depois).
            val emailDoCadastro = if (senhaParaTrocarEmail != null) authRepository.emailLogado() else email
            val motorista = Motorista(
                nome = nome, telefone = telefone, email = emailDoCadastro,
                veiculo = veiculo, placa = placa, cor = cor, foto = foto
            )
            motoristaRepository.salvarMeuCadastro(motorista, cadastroNovo = !editando)
                .onSuccess {
                    when {
                        !editando -> concluirCadastroNovo(email)
                        senhaParaTrocarEmail != null -> avisarTrocaDeEmail(email)
                        else -> {
                            Toast.makeText(this@CadastroMotoristaActivity, "✅ Dados atualizados!", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
                .onFailure { e ->
                    binding.btnCadastrar.isEnabled = true
                    val prefixo = if (editando) "Erro ao atualizar" else "Erro ao salvar dados"
                    Toast.makeText(this@CadastroMotoristaActivity, "$prefixo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun pedirSenhaParaTrocarEmail(aoConfirmar: (String) -> Unit) {
        val inputSenha = EditText(this).apply {
            hint = "Sua senha atual"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Trocar e-mail de login")
            .setMessage("Para trocar o e-mail, confirme sua senha. Vamos mandar um link para o e-mail novo — o login só muda depois que você abrir esse link.")
            .setView(inputSenha)
            .setPositiveButton("Confirmar") { _, _ ->
                val senha = inputSenha.text.toString()
                if (senha.isEmpty()) {
                    Toast.makeText(this, "Digite sua senha", Toast.LENGTH_SHORT).show()
                } else {
                    aoConfirmar(senha)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun avisarTrocaDeEmail(email: String) {
        AlertDialog.Builder(this)
            .setTitle("✅ Dados atualizados!")
            .setMessage("Enviamos um link para $email. Abra o link para confirmar o novo e-mail; até lá, continue entrando com o e-mail antigo.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
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
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    private fun EditText.textoLimpo() = text.toString().trim()

    private fun EditText.erro(mensagem: String) {
        error = mensagem
        requestFocus()
    }

    companion object {
        private const val EXTRA_EDITANDO = "motorista"

        fun intentEdicao(context: Context): Intent =
            Intent(context, CadastroMotoristaActivity::class.java).putExtra(EXTRA_EDITANDO, true)
    }
}
