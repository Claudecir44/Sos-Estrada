package com.cjstudio.sosestrada;

public class Motorista {
    private String uid, nome, telefone, email, veiculo, placa, cor;

    public Motorista() {} // necessário para Firestore

    // Getters e Setters (gerar todos)
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getVeiculo() { return veiculo; }
    public void setVeiculo(String veiculo) { this.veiculo = veiculo; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
}