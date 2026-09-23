package com.cjstudio.sosestrada;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_SOLICITACAO_ID = "solicitacao_id";
    public static final String EXTRA_MEU_TIPO = "meu_tipo"; // "motorista" ou "prestador"
    public static final String EXTRA_TITULO = "titulo_chat";
    public static final String EXTRA_READ_ONLY = "read_only";

    private RecyclerView rvMensagens;
    private TextView tvEmptyChat;
    private EditText edtMensagem;
    private View btnEnviarMensagem;
    private ImageButton btnAnexarFoto;
    private View llInputMensagem;

    private FirebaseFirestore db;
    private DocumentReference solicitacaoRef;
    private CollectionReference mensagensRef;
    private ListenerRegistration listenerRegistration;
    private ChatAdapter adapter;
    private final List<Mensagem> mensagensList = new ArrayList<>();

    private String solicitacaoId;
    private String meuTipo;
    private boolean readOnly;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        solicitacaoId = getIntent().getStringExtra(EXTRA_SOLICITACAO_ID);
        meuTipo = getIntent().getStringExtra(EXTRA_MEU_TIPO);
        readOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);
        String titulo = getIntent().getStringExtra(EXTRA_TITULO);

        if (TextUtils.isEmpty(solicitacaoId)) {
            Toast.makeText(this, "Serviço inválido para o chat.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        solicitacaoRef = db.collection("solicitacoes").document(solicitacaoId);
        mensagensRef = solicitacaoRef.collection("mensagens");

        TextView tvTitulo = findViewById(R.id.tvTituloChat);
        TextView btnVoltar = findViewById(R.id.btnVoltarChat);
        rvMensagens = findViewById(R.id.rvMensagens);
        tvEmptyChat = findViewById(R.id.tvEmptyChat);
        edtMensagem = findViewById(R.id.edtMensagem);
        btnEnviarMensagem = findViewById(R.id.btnEnviarMensagem);
        btnAnexarFoto = findViewById(R.id.btnAnexarFoto);
        llInputMensagem = findViewById(R.id.llInputMensagem);

        tvTitulo.setText(!TextUtils.isEmpty(titulo) ? titulo : "Chat");
        btnVoltar.setOnClickListener(v -> finish());

        rvMensagens.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(mensagensList, this, readOnly ? null : meuTipo);
        rvMensagens.setAdapter(adapter);

        if (readOnly) {
            llInputMensagem.setVisibility(View.GONE);
        } else {
            pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    enviarImagem(uri);
                }
            });

            btnAnexarFoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
            btnEnviarMensagem.setOnClickListener(v -> enviarTexto());

            zerarContadorNaoLidas();
        }

        limparMensagensAntigas();
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenerRegistration = mensagensRef.orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    mensagensList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Mensagem m = doc.toObject(Mensagem.class);
                        m.setId(doc.getId());
                        mensagensList.add(m);
                    }
                    adapter.notifyDataSetChanged();
                    tvEmptyChat.setVisibility(mensagensList.isEmpty() ? View.VISIBLE : View.GONE);
                    if (!mensagensList.isEmpty()) {
                        rvMensagens.scrollToPosition(mensagensList.size() - 1);
                    }
                    if (!readOnly) {
                        marcarComoLidas(snapshots.getDocuments());
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

    private void marcarComoLidas(List<DocumentSnapshot> documentos) {
        for (DocumentSnapshot doc : documentos) {
            String remetenteTipo = doc.getString("remetenteTipo");
            Boolean lida = doc.getBoolean("lida");
            if (remetenteTipo != null && !remetenteTipo.equals(meuTipo) && (lida == null || !lida)) {
                doc.getReference().update("lida", true);
            }
        }
    }

    private void enviarTexto() {
        String texto = edtMensagem.getText().toString().trim();
        if (TextUtils.isEmpty(texto)) {
            return;
        }
        salvarMensagem(texto, null);
        edtMensagem.setText("");
    }

    private void enviarImagem(Uri uri) {
        Toast.makeText(this, "Enviando foto...", Toast.LENGTH_SHORT).show();
        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("mensagens_imagens/" + solicitacaoId + "/" + UUID.randomUUID() + ".jpg");

        ref.putFile(uri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> salvarMensagem(null, downloadUri.toString()))
                .addOnFailureListener(e -> Toast.makeText(this, "Erro ao enviar foto: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void salvarMensagem(@Nullable String texto, @Nullable String imagemUrl) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        Map<String, Object> mensagem = new HashMap<>();
        mensagem.put("remetenteUid", uid);
        mensagem.put("remetenteTipo", meuTipo);
        mensagem.put("texto", texto);
        mensagem.put("imagemUrl", imagemUrl);
        mensagem.put("timestamp", new Date());
        mensagem.put("lida", false);

        mensagensRef.add(mensagem)
                .addOnSuccessListener(doc -> incrementarContadorNaoLidas())
                .addOnFailureListener(e -> Toast.makeText(this, "Erro ao enviar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // Contador agregado em solicitacoes/{id} (naoLidasMotorista/naoLidasPrestador),
    // usado pelas listas de motorista/prestador pra mostrar um badge sem precisar
    // abrir o chat — evita ter que contar mensagens "lida:false" com uma query por
    // item de lista toda vez que a tela é montada.
    private void incrementarContadorNaoLidas() {
        String campoDestino = "motorista".equals(meuTipo) ? "naoLidasPrestador" : "naoLidasMotorista";
        solicitacaoRef.update(campoDestino, FieldValue.increment(1));
    }

    private void zerarContadorNaoLidas() {
        String meuCampo = "motorista".equals(meuTipo) ? "naoLidasMotorista" : "naoLidasPrestador";
        solicitacaoRef.update(meuCampo, 0);
    }

    private void limparMensagensAntigas() {
        Calendar limite = Calendar.getInstance();
        limite.add(Calendar.MONTH, -6);

        mensagensRef.whereLessThan("timestamp", limite.getTime())
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String imagemUrl = doc.getString("imagemUrl");
                        if (!TextUtils.isEmpty(imagemUrl)) {
                            try {
                                FirebaseStorage.getInstance().getReferenceFromUrl(imagemUrl)
                                        .delete()
                                        .addOnFailureListener(e -> { /* imagem já pode ter sido removida */ });
                            } catch (Exception ignored) { }
                        }
                        doc.getReference().delete();
                    }
                });
    }
}
