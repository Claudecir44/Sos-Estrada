package com.cjstudio.sosestrada;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CadastroPrestadorActivity extends AppCompatActivity {

    private TextInputEditText edtNome, edtCnpj, edtTelefone, edtEmail, edtSenha,
            edtServico, edtPreco;
    // Novos campos de endereço
    private TextInputEditText edtRua, edtNumero, edtBairro, edtCidade, edtComplemento, edtEstado, edtPais;
    private ImageView ivLogo;
    private MaterialButton btnCadastrar, btnSelecionarLogo;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private boolean isEditando = false;
    private Uri imagemUri = null;
    private String logoUrlAtual = null;
    private String uid;

    // Launcher para selecionar imagem da galeria
    private final ActivityResultLauncher<Intent> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            imagemUri = result.getData().getData();
                            if (imagemUri != null) {
                                Glide.with(this)
                                        .load(imagemUri)
                                        .centerCrop()
                                        .into(ivLogo);
                                logoUrlAtual = null;
                            }
                        } else {
                            Toast.makeText(this, "Nenhuma imagem selecionada", Toast.LENGTH_SHORT).show();
                        }
                    });

    // Launcher para solicitar permissão de leitura de imagens
    private final ActivityResultLauncher<String> permissaoGaleriaLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    abrirGaleria();
                } else {
                    Toast.makeText(this, "Permissão negada! Não é possível selecionar imagem.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_prestador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Inicializar views
        ivLogo = findViewById(R.id.ivLogo);
        btnSelecionarLogo = findViewById(R.id.btnSelecionarLogo);
        edtNome = findViewById(R.id.edtNome);
        edtCnpj = findViewById(R.id.edtCnpj);
        edtTelefone = findViewById(R.id.edtTelefone);
        edtEmail = findViewById(R.id.edtEmail);
        edtSenha = findViewById(R.id.edtSenha);
        edtServico = findViewById(R.id.edtServico);
        edtPreco = findViewById(R.id.edtPreco);
        // Novos campos
        edtRua = findViewById(R.id.edtRua);
        edtNumero = findViewById(R.id.edtNumero);
        edtBairro = findViewById(R.id.edtBairro);
        edtCidade = findViewById(R.id.edtCidade);
        edtComplemento = findViewById(R.id.edtComplemento);
        edtEstado = findViewById(R.id.edtEstado);
        edtPais = findViewById(R.id.edtPais);
        btnCadastrar = findViewById(R.id.btnCadastrar);

        // Máscara de telefone
        aplicarMascaraTelefone();

        // Clique na imagem ou no botão para selecionar logo
        ivLogo.setOnClickListener(v -> verificarPermissaoEabrirGaleria());
        btnSelecionarLogo.setOnClickListener(v -> verificarPermissaoEabrirGaleria());

        // Verifica se é edição
        Intent intent = getIntent();
        if (intent.getBooleanExtra("prestador", false)) {
            isEditando = true;
            edtNome.setText(intent.getStringExtra("prestador_nome"));
            edtCnpj.setText(intent.getStringExtra("prestador_cnpj"));
            edtTelefone.setText(intent.getStringExtra("prestador_telefone"));
            edtEmail.setText(intent.getStringExtra("prestador_email"));
            edtServico.setText(intent.getStringExtra("prestador_servico"));
            edtPreco.setText(intent.getStringExtra("prestador_preco"));
            // Novos campos de endereço na edição
            edtRua.setText(intent.getStringExtra("prestador_rua"));
            edtNumero.setText(intent.getStringExtra("prestador_numero"));
            edtBairro.setText(intent.getStringExtra("prestador_bairro"));
            edtCidade.setText(intent.getStringExtra("prestador_cidade"));
            edtComplemento.setText(intent.getStringExtra("prestador_complemento"));
            edtEstado.setText(intent.getStringExtra("prestador_estado"));
            edtPais.setText(intent.getStringExtra("prestador_pais"));
            logoUrlAtual = intent.getStringExtra("prestador_logo");
            uid = intent.getStringExtra("prestador_uid");

            if (logoUrlAtual != null && !logoUrlAtual.isEmpty()) {
                Glide.with(this)
                        .load(logoUrlAtual)
                        .centerCrop()
                        .placeholder(R.drawable.ic_placeholder_logo)
                        .into(ivLogo);
            }
            btnCadastrar.setText("ATUALIZAR CADASTRO");
        } else {
            btnCadastrar.setText("CADASTRAR");
        }

        btnCadastrar.setOnClickListener(v -> realizarCadastro());
    }

    // Método para verificar permissão e abrir galeria
    private void verificarPermissaoEabrirGaleria() {
        String permissao;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissao = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permissao = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED) {
            abrirGaleria();
        } else {
            permissaoGaleriaLauncher.launch(permissao);
        }
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galeriaLauncher.launch(intent);
    }

    // Máscara de telefone (XX) XXXXX-XXXX
    private void aplicarMascaraTelefone() {
        edtTelefone.addTextChangedListener(new TextWatcher() {
            private boolean isUpdating = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                String raw = s.toString().replaceAll("[^0-9]", "");
                if (raw.length() > 11) raw = raw.substring(0, 11);

                StringBuilder formatted = new StringBuilder();
                if (raw.length() > 0) {
                    formatted.append("(").append(raw.substring(0, Math.min(2, raw.length())));
                }
                if (raw.length() >= 3) {
                    formatted.append(") ").append(raw.substring(2, Math.min(6, raw.length())));
                }
                if (raw.length() >= 7) {
                    formatted.append("-").append(raw.substring(6, Math.min(11, raw.length())));
                }

                isUpdating = true;
                s.replace(0, s.length(), formatted.toString());
                isUpdating = false;
            }
        });
    }

    private void realizarCadastro() {
        String nome = edtNome.getText().toString().trim();
        String cnpj = edtCnpj.getText().toString().trim();
        String telefone = edtTelefone.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String senha = edtSenha.getText().toString().trim();
        String servico = edtServico.getText().toString().trim();
        String preco = edtPreco.getText().toString().trim();

        // Novos campos de endereço
        String rua = edtRua.getText().toString().trim();
        String numero = edtNumero.getText().toString().trim();
        String bairro = edtBairro.getText().toString().trim();
        String cidade = edtCidade.getText().toString().trim();
        String complemento = edtComplemento.getText().toString().trim();
        String estado = edtEstado.getText().toString().trim().toUpperCase();
        String pais = edtPais.getText().toString().trim();

        // Validações obrigatórias
        if (TextUtils.isEmpty(nome)) {
            edtNome.setError("Nome obrigatório");
            edtNome.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(telefone)) {
            edtTelefone.setError("Telefone obrigatório");
            edtTelefone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("E-mail obrigatório");
            edtEmail.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("E-mail inválido");
            edtEmail.requestFocus();
            return;
        }
        if (!isEditando && TextUtils.isEmpty(senha)) {
            edtSenha.setError("Senha obrigatória para novo cadastro");
            edtSenha.requestFocus();
            return;
        }
        if (!isEditando && senha.length() < 6) {
            edtSenha.setError("Senha deve ter pelo menos 6 caracteres");
            edtSenha.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(servico)) {
            edtServico.setError("Tipo de serviço obrigatório");
            edtServico.requestFocus();
            return;
        }

        // Validações de endereço (todos obrigatórios, exceto complemento)
        if (TextUtils.isEmpty(rua)) {
            edtRua.setError("Rua obrigatória");
            edtRua.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(numero)) {
            edtNumero.setError("Número obrigatório");
            edtNumero.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(bairro)) {
            edtBairro.setError("Bairro obrigatório");
            edtBairro.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(cidade)) {
            edtCidade.setError("Cidade obrigatória");
            edtCidade.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(estado)) {
            edtEstado.setError("Estado obrigatório");
            edtEstado.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(pais)) {
            edtPais.setError("País obrigatório");
            edtPais.requestFocus();
            return;
        }

        if (isEditando) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                uid = user.getUid();
                if (imagemUri != null) {
                    fazerUploadImagem(uid, () -> salvarDadosPrestador(uid, nome, cnpj, telefone, email, servico, preco,
                            rua, numero, bairro, cidade, complemento, estado, pais, true));
                } else {
                    salvarDadosPrestador(uid, nome, cnpj, telefone, email, servico, preco,
                            rua, numero, bairro, cidade, complemento, estado, pais, true);
                }
            } else {
                Toast.makeText(this, "Usuário não autenticado", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            uid = user.getUid();
                            if (imagemUri != null) {
                                fazerUploadImagem(uid, () -> salvarDadosPrestador(uid, nome, cnpj, telefone, email, servico, preco,
                                        rua, numero, bairro, cidade, complemento, estado, pais, false));
                            } else {
                                salvarDadosPrestador(uid, nome, cnpj, telefone, email, servico, preco,
                                        rua, numero, bairro, cidade, complemento, estado, pais, false);
                            }
                        }
                    } else {
                        Toast.makeText(this, "Erro ao criar conta: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    interface UploadCallback {
        void onComplete();
    }

    private void fazerUploadImagem(String uid, UploadCallback callback) {
        if (imagemUri == null) {
            if (callback != null) callback.onComplete();
            return;
        }

        String nomeArquivo = "logos/" + uid + "/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child(nomeArquivo);

        ref.putFile(imagemUri)
                .addOnSuccessListener(taskSnapshot -> {
                    ref.getDownloadUrl().addOnSuccessListener(uri -> {
                        logoUrlAtual = uri.toString();
                        if (callback != null) callback.onComplete();
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Erro ao obter URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.onComplete();
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro no upload: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onComplete();
                });
    }

    private void salvarDadosPrestador(String uid, String nome, String cnpj, String telefone, String email,
                                      String servico, String preco,
                                      String rua, String numero, String bairro, String cidade,
                                      String complemento, String estado, String pais,
                                      boolean isEdicao) {
        Map<String, Object> prestador = new HashMap<>();
        prestador.put("nome", nome);
        prestador.put("cnpj", cnpj);
        prestador.put("telefone", telefone);
        prestador.put("email", email);
        prestador.put("servico", servico);
        prestador.put("preco", preco);
        prestador.put("uid", uid);
        // Novos campos de endereço
        prestador.put("rua", rua);
        prestador.put("numero", numero);
        prestador.put("bairro", bairro);
        prestador.put("cidade", cidade);
        prestador.put("complemento", complemento);
        prestador.put("estado", estado);
        prestador.put("pais", pais);

        if (logoUrlAtual != null && !logoUrlAtual.isEmpty()) {
            prestador.put("logo", logoUrlAtual);
        }

        db.collection("prestadores").document(uid)
                .set(prestador)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, isEdicao ? "✅ Dados atualizados!" : "✅ Cadastro realizado!", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(CadastroPrestadorActivity.this, LoginPrestadorActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}