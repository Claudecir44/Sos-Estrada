package com.cjstudio.sosestrada;

import java.util.Date;

public class Solicitacao {
    private String id;
    private String motoristaUid;
    private String prestadorUid;
    private String prestadorNome;
    private String motoristaNome;
    private String motoristaTelefone;
    private String motoristaVeiculo;
    private String motoristaPlaca;
    private String status; // pendente, aceito, recusado, finalizado
    private Date timestamp;
    private double latitudeMotorista;
    private double longitudeMotorista;
    private String enderecoMotorista;

    public Solicitacao() {}

    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMotoristaUid() { return motoristaUid; }
    public void setMotoristaUid(String motoristaUid) { this.motoristaUid = motoristaUid; }
    public String getPrestadorUid() { return prestadorUid; }
    public void setPrestadorUid(String prestadorUid) { this.prestadorUid = prestadorUid; }
    public String getPrestadorNome() { return prestadorNome; }
    public void setPrestadorNome(String prestadorNome) { this.prestadorNome = prestadorNome; }
    public String getMotoristaNome() { return motoristaNome; }
    public void setMotoristaNome(String motoristaNome) { this.motoristaNome = motoristaNome; }
    public String getMotoristaTelefone() { return motoristaTelefone; }
    public void setMotoristaTelefone(String motoristaTelefone) { this.motoristaTelefone = motoristaTelefone; }
    public String getMotoristaVeiculo() { return motoristaVeiculo; }
    public void setMotoristaVeiculo(String motoristaVeiculo) { this.motoristaVeiculo = motoristaVeiculo; }
    public String getMotoristaPlaca() { return motoristaPlaca; }
    public void setMotoristaPlaca(String motoristaPlaca) { this.motoristaPlaca = motoristaPlaca; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public double getLatitudeMotorista() { return latitudeMotorista; }
    public void setLatitudeMotorista(double latitudeMotorista) { this.latitudeMotorista = latitudeMotorista; }
    public double getLongitudeMotorista() { return longitudeMotorista; }
    public void setLongitudeMotorista(double longitudeMotorista) { this.longitudeMotorista = longitudeMotorista; }
    public String getEnderecoMotorista() { return enderecoMotorista; }
    public void setEnderecoMotorista(String enderecoMotorista) { this.enderecoMotorista = enderecoMotorista; }
}