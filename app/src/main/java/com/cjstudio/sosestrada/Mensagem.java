package com.cjstudio.sosestrada;

import java.util.Date;

public class Mensagem {
    private String id;
    private String remetenteUid;
    private String remetenteTipo; // "motorista" ou "prestador"
    private String texto;
    private String imagemUrl;
    private Date timestamp;
    private boolean lida;

    public Mensagem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRemetenteUid() { return remetenteUid; }
    public void setRemetenteUid(String remetenteUid) { this.remetenteUid = remetenteUid; }
    public String getRemetenteTipo() { return remetenteTipo; }
    public void setRemetenteTipo(String remetenteTipo) { this.remetenteTipo = remetenteTipo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getImagemUrl() { return imagemUrl; }
    public void setImagemUrl(String imagemUrl) { this.imagemUrl = imagemUrl; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public boolean isLida() { return lida; }
    public void setLida(boolean lida) { this.lida = lida; }
}
