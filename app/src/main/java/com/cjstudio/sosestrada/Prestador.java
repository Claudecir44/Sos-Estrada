package com.cjstudio.sosestrada;

public class Prestador {
    private String uid, nome, cnpj, telefone, email, servico, preco, logo;
    // Campos antigos (fallback)
    private String localizacao, endereco;
    // Novos campos
    private String rua, numero, bairro, cidade, complemento, estado, pais;
    private double distancia;
    // Status da solicitação (pendente, aceito, recusado ou null)
    private String statusSolicitacao;
    // Id da solicitação ativa com este prestador (usado para abrir o chat)
    private String solicitacaoId;
    // Mensagens do prestador ainda não lidas pelo motorista, na solicitação ativa acima.
    private int naoLidasMotorista;

    public Prestador() {}

    // Getters e Setters existentes...
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getServico() { return servico; }
    public void setServico(String servico) { this.servico = servico; }
    public String getPreco() { return preco; }
    public void setPreco(String preco) { this.preco = preco; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    // Fallback getters/setters
    public String getLocalizacao() { return localizacao; }
    public void setLocalizacao(String localizacao) { this.localizacao = localizacao; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    // Novos getters e setters
    public String getRua() { return rua; }
    public void setRua(String rua) { this.rua = rua; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }

    public double getDistancia() { return distancia; }
    public void setDistancia(double distancia) { this.distancia = distancia; }

    // Getter e Setter para statusSolicitacao
    public String getStatusSolicitacao() { return statusSolicitacao; }
    public void setStatusSolicitacao(String statusSolicitacao) { this.statusSolicitacao = statusSolicitacao; }

    public String getSolicitacaoId() { return solicitacaoId; }
    public void setSolicitacaoId(String solicitacaoId) { this.solicitacaoId = solicitacaoId; }

    public int getNaoLidasMotorista() { return naoLidasMotorista; }
    public void setNaoLidasMotorista(int naoLidasMotorista) { this.naoLidasMotorista = naoLidasMotorista; }

    // Método que monta o endereço completo (com fallback)
    public String getEnderecoCompleto() {
        // Tenta usar os campos novos
        StringBuilder sb = new StringBuilder();
        if (rua != null && !rua.isEmpty()) sb.append(rua);
        if (numero != null && !numero.isEmpty()) sb.append(", ").append(numero);
        if (bairro != null && !bairro.isEmpty()) sb.append(" - ").append(bairro);
        if (cidade != null && !cidade.isEmpty()) sb.append(", ").append(cidade);
        if (estado != null && !estado.isEmpty()) sb.append(" - ").append(estado);
        if (pais != null && !pais.isEmpty()) sb.append(", ").append(pais);
        if (complemento != null && !complemento.isEmpty()) sb.append(" (").append(complemento).append(")");

        // Se não tem campos novos, usa os antigos
        if (sb.length() == 0) {
            if (endereco != null && !endereco.isEmpty()) {
                return endereco;
            }
            if (localizacao != null && !localizacao.isEmpty()) {
                return localizacao;
            }
            return "Endereço não informado";
        }
        return sb.toString();
    }
}