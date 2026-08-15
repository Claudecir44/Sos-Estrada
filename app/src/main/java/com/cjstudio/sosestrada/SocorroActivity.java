package com.cjstudio.sosestrada;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SocorroActivity extends AppCompatActivity implements
        PrestadorAdapter.OnSolicitarServicoListener,
        PrestadorAdapter.OnItemLongClickListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextInputEditText edtPesquisa;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private PrestadorAdapter adapter;
    private List<Prestador> prestadorList = new ArrayList<>();

    private double driverLat = 0;
    private double driverLng = 0;
    private boolean locationReady = false;

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socorro);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        recyclerView = findViewById(R.id.rvPrestadoresSocorro);
        progressBar = findViewById(R.id.progressBarSocorro);
        tvEmpty = findViewById(R.id.tvEmptySocorro);
        edtPesquisa = findViewById(R.id.edtPesquisa);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PrestadorAdapter(prestadorList, this, this, this);
        recyclerView.setAdapter(adapter);

        edtPesquisa.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        verificarPermissaoLocalizacao();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationReady) {
            carregarPrestadores();
        }
    }

    private void verificarPermissaoLocalizacao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            obterLocalizacaoAtual();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                obterLocalizacaoAtual();
            } else {
                Toast.makeText(this, "Permissão de localização negada. A distância não será calculada.", Toast.LENGTH_LONG).show();
                carregarPrestadores();
            }
        }
    }

    private void obterLocalizacaoAtual() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        driverLat = location.getLatitude();
                        driverLng = location.getLongitude();
                        locationReady = true;
                        Log.d("Socorro", "Localização obtida: " + driverLat + ", " + driverLng);
                        carregarPrestadores();
                    } else {
                        Toast.makeText(this, "Não foi possível obter a localização. Verifique o GPS.", Toast.LENGTH_SHORT).show();
                        carregarPrestadores();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erro ao obter localização: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    carregarPrestadores();
                });
    }

    private void carregarPrestadores() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        db.collection("prestadores")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        List<Prestador> tempList = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Prestador p = doc.toObject(Prestador.class);
                            tempList.add(p);
                        }

                        if (tempList.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            adapter.updateList(new ArrayList<>());
                            return;
                        }

                        if (!locationReady) {
                            carregarStatusSolicitacoes(tempList);
                            return;
                        }

                        calcularDistancias(tempList);

                    } else {
                        Toast.makeText(this, "Erro ao carregar prestadores: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void calcularDistancias(final List<Prestador> lista) {
        new Thread(() -> {
            Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
            List<Prestador> listaComDistancia = new ArrayList<>();

            for (Prestador p : lista) {
                try {
                    String endereco = p.getEnderecoCompleto();
                    if (endereco != null && !endereco.isEmpty()
                            && !endereco.equals(" ")
                            && !endereco.equals("Endereço não informado")
                            && driverLat != 0 && driverLng != 0) {
                        if (!endereco.toLowerCase().contains("brasil")) {
                            endereco += ", Brasil";
                        }
                        List<Address> addresses = geocoder.getFromLocationName(endereco, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address address = addresses.get(0);
                            double prestadorLat = address.getLatitude();
                            double prestadorLng = address.getLongitude();

                            float[] results = new float[1];
                            Location.distanceBetween(driverLat, driverLng, prestadorLat, prestadorLng, results);
                            float distanciaKm = results[0] / 1000;
                            p.setDistancia(distanciaKm);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                listaComDistancia.add(p);
            }

            runOnUiThread(() -> {
                carregarStatusSolicitacoes(listaComDistancia);
            });
        }).start();
    }

    private void carregarStatusSolicitacoes(final List<Prestador> tempList) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            adapter.updateList(tempList);
            return;
        }

        db.collection("solicitacoes")
                .whereEqualTo("motoristaUid", user.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, String> statusMap = new HashMap<>();
                    Map<String, String> idMap = new HashMap<>();
                    Map<String, Long> naoLidasMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String prestadorUid = doc.getString("prestadorUid");
                        String status = doc.getString("status");
                        if (prestadorUid != null && status != null) {
                            statusMap.put(prestadorUid, status);
                            idMap.put(prestadorUid, doc.getId());
                            naoLidasMap.put(prestadorUid, doc.getLong("naoLidasMotorista"));
                        }
                    }
                    for (Prestador p : tempList) {
                        String status = statusMap.get(p.getUid());
                        p.setStatusSolicitacao(status);
                        p.setSolicitacaoId(idMap.get(p.getUid()));
                        Long naoLidas = naoLidasMap.get(p.getUid());
                        p.setNaoLidasMotorista(naoLidas != null ? naoLidas.intValue() : 0);
                    }
                    prestadorList.clear();
                    prestadorList.addAll(tempList);
                    adapter.updateList(prestadorList);
                })
                .addOnFailureListener(e -> {
                    adapter.updateList(tempList);
                });
    }

    // ---------- OnSolicitarServicoListener ----------
    @Override
    public void onSolicitarServico(Prestador prestador) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Faça login como motorista primeiro", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("solicitacoes")
                .whereEqualTo("motoristaUid", user.getUid())
                .whereEqualTo("prestadorUid", prestador.getUid())
                .whereIn("status", List.of("pendente", "aceito"))
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Toast.makeText(this, "Você já possui uma solicitação pendente ou aceita para este prestador.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("motoristas").document(user.getUid())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!documentSnapshot.exists()) {
                                    Toast.makeText(this, "Dados do motorista não encontrados.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                String nomeMotorista = documentSnapshot.getString("nome");
                                String telefoneMotorista = documentSnapshot.getString("telefone");
                                String veiculoMotorista = documentSnapshot.getString("veiculo");
                                String placaMotorista = documentSnapshot.getString("placa");

                                Map<String, Object> solicitacao = new HashMap<>();
                                solicitacao.put("motoristaUid", user.getUid());
                                solicitacao.put("prestadorUid", prestador.getUid());
                                solicitacao.put("prestadorNome", prestador.getNome());
                                solicitacao.put("motoristaNome", nomeMotorista);
                                solicitacao.put("motoristaTelefone", telefoneMotorista);
                                solicitacao.put("motoristaVeiculo", veiculoMotorista);
                                solicitacao.put("motoristaPlaca", placaMotorista);
                                solicitacao.put("status", "pendente");
                                solicitacao.put("timestamp", new Date());
                                solicitacao.put("latitudeMotorista", driverLat);
                                solicitacao.put("longitudeMotorista", driverLng);

                                String enderecoMotorista = obterEnderecoAtual(driverLat, driverLng);
                                if (enderecoMotorista != null) {
                                    solicitacao.put("enderecoMotorista", enderecoMotorista);
                                }

                                db.collection("solicitacoes")
                                        .add(solicitacao)
                                        .addOnSuccessListener(documentReference -> {
                                            Toast.makeText(this, "✅ Solicitação enviada para " + prestador.getNome(), Toast.LENGTH_LONG).show();
                                            prestador.setStatusSolicitacao("pendente");
                                            adapter.notifyDataSetChanged();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Erro ao enviar solicitação: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        });

                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Erro ao buscar dados do motorista: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });
    }

    // ---------- OnItemLongClickListener ----------
    @Override
    public void onItemLongClick(Prestador prestador) {
        if (prestador.getStatusSolicitacao() == null) {
            Toast.makeText(this, "Não há solicitação ativa para este prestador.", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("cancelado".equals(prestador.getStatusSolicitacao())) {
            new AlertDialog.Builder(this)
                    .setTitle("Excluir permanentemente")
                    .setMessage("Esta solicitação já foi cancelada. Deseja excluí-la permanentemente?")
                    .setPositiveButton("Sim", (dialog, which) -> excluirPermanentemente(prestador))
                    .setNegativeButton("Não", null)
                    .show();
            return;
        }

        mostrarDialogoCancelamento(prestador);
    }

    private void mostrarDialogoCancelamento(Prestador prestador) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_cancelar_solicitacao, null);
        CheckBox checkBox = view.findViewById(R.id.checkBoxConfirmacaoCancelamento);
        EditText edtSenha = view.findViewById(R.id.edtSenhaCancelamento);

        builder.setView(view);
        builder.setTitle("Cancelar solicitação");

        builder.setPositiveButton("Cancelar solicitação", (dialog, which) -> {
            if (!checkBox.isChecked()) {
                Toast.makeText(this, "Marque a checkbox para confirmar.", Toast.LENGTH_SHORT).show();
                return;
            }
            String senha = edtSenha.getText().toString().trim();
            if (TextUtils.isEmpty(senha)) {
                Toast.makeText(this, "Digite sua senha.", Toast.LENGTH_SHORT).show();
                return;
            }
            reautenticarECancelar(prestador, senha);
        });

        builder.setNegativeButton("Voltar", null);
        builder.show();
    }

    private void reautenticarECancelar(Prestador prestador, String senha) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Usuário não logado", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = user.getEmail();
        if (email == null) {
            Toast.makeText(this, "E-mail não disponível", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        cancelarSolicitacao(prestador);
                    } else {
                        Toast.makeText(this, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void cancelarSolicitacao(Prestador prestador) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("solicitacoes")
                .whereEqualTo("motoristaUid", user.getUid())
                .whereEqualTo("prestadorUid", prestador.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            doc.getReference().update("status", "cancelado")
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Solicitação cancelada com sucesso.", Toast.LENGTH_SHORT).show();
                                        prestador.setStatusSolicitacao("cancelado");
                                        adapter.notifyDataSetChanged();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Erro ao cancelar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        Toast.makeText(this, "Nenhuma solicitação encontrada.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void excluirPermanentemente(Prestador prestador) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("solicitacoes")
                .whereEqualTo("motoristaUid", user.getUid())
                .whereEqualTo("prestadorUid", prestador.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            doc.getReference().delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Solicitação excluída permanentemente.", Toast.LENGTH_SHORT).show();
                                        prestador.setStatusSolicitacao(null);
                                        adapter.notifyDataSetChanged();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        Toast.makeText(this, "Nenhuma solicitação encontrada.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String obterEnderecoAtual(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}