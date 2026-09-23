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
import com.cjstudio.sosestrada.databinding.ActivityCadastroAdminBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Duas telas numa só (mesmo modelo do Caronas):
// - Cadastro de admin: dados + foto (obrigatória) + senha de login + senha do
//   administrador master, que autoriza a criação.
// - Meu Perfil (intentPerfil): o cadastro do admin logado já preenchido, pra
//   editar e salvar (e-mail e senha de login não mudam aqui) ou excluir.
@AndroidEntryPoint
class CadastroAdminActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: IAdminRepository

    private lateinit var binding: ActivityCadastroAdminBinding
    private var modoPerfil = false
    private var fotoNova: Uri? = null
    private var temFotoSalva = false

    private val seletorFoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            fotoNova = uri
            binding.ivFotoAdmin.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modoPerfil = intent.getBooleanExtra(EXTRA_PERFIL, false)
        TelefoneUtil.aplicarMascara(binding.edtTelefone)
        CpfUtil.aplicarMascara(binding.edtCpf)
        binding.btnVoltar.setOnClickListener { finish() }

        val abrirSeletor = View.OnClickListener {
            seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.ivFotoAdmin.setOnClickListener(abrirSeletor)
        binding.tvSelecionarFotoAdmin.setOnClickListener(abrirSeletor)

        if (modoPerfil) {
            binding.tvTituloCadastroAdmin.text = "Meu Perfil"
            binding.tvSelecionarFotoAdmin.text = "📷 Trocar foto"
            binding.btnCadastrar.text = "SALVAR ALTERAÇÕES"
            binding.edtEmail.isEnabled = false
            binding.layoutSenhas.visibility = View.GONE
            binding.btnExcluir.visibility = View.VISIBLE
            binding.btnExcluir.setOnClickListener { confirmarExclusao() }
            binding.btnCadastrar.setOnClickListener { salvarPerfil() }
            carregarPerfil()
        } else {
            binding.btnCadastrar.setOnClickListener { cadastrar() }
        }
    }

    private fun carregarPerfil() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            adminRepository.buscarMeuCadastro()
                .onSuccess { admin ->
                    if (admin == null) {
                        Toast.makeText(this@CadastroAdminActivity, "Cadastro de admin não encontrado.", Toast.LENGTH_LONG).show()
                        finish()
                        return@onSuccess
                    }
                    binding.edtNome.setText(admin.nome)
                    binding.edtSobrenome.setText(admin.sobrenome)
                    binding.edtEmail.setText(admin.email ?: adminRepository.emailLogado())
                    binding.edtTelefone.setText(admin.telefone)
                    binding.edtCpf.setText(admin.cpf)
                    temFotoSalva = !admin.foto.isNullOrEmpty()
                    // Só mostra a salva se a pessoa ainda não escolheu outra.
                    if (fotoNova == null) FotoUtil.mostrar(binding.ivFotoAdmin, admin.foto)
                }
                .onFailure { e ->
                    Toast.makeText(this@CadastroAdminActivity, "Erro ao carregar o perfil: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            binding.progressBar.visibility = View.GONE
        }
    }

    // Validação comum ao cadastro e ao perfil; devolve o Admin ou null.
    private fun lerDadosPessoais(): Admin? {
        val nome = binding.edtNome.textoLimpo()
        val sobrenome = binding.edtSobrenome.textoLimpo()
        val email = binding.edtEmail.textoLimpo()
        val telefone = binding.edtTelefone.textoLimpo()
        val cpf = binding.edtCpf.textoLimpo()
        when {
            fotoNova == null && !temFotoSalva -> {
                Toast.makeText(this, "Selecione uma foto.", Toast.LENGTH_SHORT).show()
                return null
            }
            nome.isEmpty() -> return binding.edtNome.erro("Informe o nome")
            sobrenome.isEmpty() -> return binding.edtSobrenome.erro("Informe o sobrenome")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return binding.edtEmail.erro("E-mail inválido")
            telefone.isEmpty() -> return binding.edtTelefone.erro("Informe o telefone")
            !CpfUtil.valido(cpf) -> return binding.edtCpf.erro("CPF inválido")
        }
        return Admin(nome = nome, sobrenome = sobrenome, email = email, telefone = telefone, cpf = cpf)
    }

    private fun cadastrar() {
        val admin = lerDadosPessoais() ?: return
        val senha = binding.edtSenha.text.toString()
        val senhaMaster = binding.edtSenhaMaster.text.toString()
        SenhaUtil.validar(senha)?.let {
            binding.edtSenha.erro(it)
            return
        }
        if (senha != binding.edtConfirmarSenha.text.toString()) {
            binding.edtConfirmarSenha.erro("As senhas não coincidem")
            return
        }
        if (senhaMaster.isEmpty()) {
            binding.edtSenhaMaster.erro("Informe a senha do administrador master")
            return
        }

        ocupado(true)
        lifecycleScope.launch {
            admin.foto = fotoNova?.let { FotoUtil.paraBase64(this@CadastroAdminActivity, it) }
            adminRepository.cadastrarAdmin(admin, senha, senhaMaster)
                .onSuccess {
                    ocupado(false)
                    val email = admin.email.orEmpty()
                    AlertDialog.Builder(this@CadastroAdminActivity)
                        .setTitle("✅ Admin cadastrado!")
                        .setMessage("Enviamos um e-mail de verificação para $email.\n\nAbra o link do e-mail (confira também o spam) e depois entre com seu e-mail e senha.")
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_EMAIL, email))
                            finish()
                        }
                        .show()
                }
                .onFailure { e ->
                    ocupado(false)
                    if (e is SenhaMasterIncorretaException) {
                        binding.edtSenhaMaster.erro(e.message.orEmpty())
                    } else {
                        Toast.makeText(this@CadastroAdminActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun salvarPerfil() {
        val admin = lerDadosPessoais() ?: return
        ocupado(true)
        lifecycleScope.launch {
            admin.foto = fotoNova?.let { FotoUtil.paraBase64(this@CadastroAdminActivity, it) }
            adminRepository.atualizarMeuCadastro(admin)
                .onSuccess {
                    Toast.makeText(this@CadastroAdminActivity, "✅ Perfil atualizado!", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure { e ->
                    ocupado(false)
                    Toast.makeText(this@CadastroAdminActivity, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun confirmarExclusao() {
        val inputSenha = EditText(this).apply {
            hint = "Digite sua senha de login"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Excluir meu perfil")
            .setMessage("Seu acesso ao painel administrativo será removido. Se esta conta não for também de motorista ou prestador, o login será apagado. Esta ação não pode ser desfeita.")
            .setView(inputSenha)
            .setPositiveButton("Excluir") { _, _ ->
                val senha = inputSenha.text.toString()
                if (senha.isEmpty()) {
                    Toast.makeText(this, "Digite sua senha", Toast.LENGTH_SHORT).show()
                } else {
                    excluir(senha)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun excluir(senha: String) {
        ocupado(true)
        lifecycleScope.launch {
            adminRepository.excluirMeuCadastro(senha)
                .onSuccess {
                    Toast.makeText(this@CadastroAdminActivity, "Perfil excluído.", Toast.LENGTH_LONG).show()
                    setResult(RESULTADO_EXCLUIDO)
                    finish()
                }
                .onFailure { e ->
                    ocupado(false)
                    Toast.makeText(this@CadastroAdminActivity, "Erro ao excluir: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun ocupado(sim: Boolean) {
        binding.progressBar.visibility = if (sim) View.VISIBLE else View.GONE
        binding.btnCadastrar.isEnabled = !sim
        binding.btnExcluir.isEnabled = !sim
    }

    private fun EditText.textoLimpo() = text.toString().trim()

    private fun EditText.erro(mensagem: String): Admin? {
        error = mensagem
        requestFocus()
        return null
    }

    companion object {
        // E-mail cadastrado, devolvido pra já vir preenchido no login.
        const val EXTRA_EMAIL = "email"
        private const val EXTRA_PERFIL = "perfil"

        // Resultado do Meu Perfil quando o admin exclui o próprio cadastro.
        const val RESULTADO_EXCLUIDO = 2

        fun intent(context: Context) = Intent(context, CadastroAdminActivity::class.java)

        fun intentPerfil(context: Context) =
            Intent(context, CadastroAdminActivity::class.java).putExtra(EXTRA_PERFIL, true)
    }
}
