package br.pucminas.pontomorto.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** Motorista ou motoboy (RF01). */
@Entity
@Table(name = "motorista")
public class Motorista {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "motorista_seq")
    @SequenceGenerator(name = "motorista_seq", sequenceName = "motorista_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Column(length = 20)
    private String documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veiculo_id")
    private Veiculo veiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gerente_id")
    private Gerente gerente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private boolean anonimizado;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Motorista() {
    }

    public Motorista(String nome, String telefone, String documento) {
        this.nome = nome;
        this.telefone = telefone;
        this.documento = documento;
    }

    /**
     * RNF06: documento mascarado nas listas. Mostra só os 3 dígitos do meio, ex.: ***.456.***-**.
     */
    public String getDocumentoMascarado() {
        return mascarar(documento);
    }

    public static String mascarar(String documento) {
        if (documento == null || documento.isBlank()) {
            return "—";
        }
        String digitos = documento.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return "***." + digitos.substring(3, 6) + ".***-**";
        }
        if (digitos.length() <= 4) {
            return "****";
        }
        return "*".repeat(digitos.length() - 3) + digitos.substring(digitos.length() - 3);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(Veiculo veiculo) {
        this.veiculo = veiculo;
    }

    public Gerente getGerente() {
        return gerente;
    }

    public void setGerente(Gerente gerente) {
        this.gerente = gerente;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public boolean isAnonimizado() {
        return anonimizado;
    }

    public void setAnonimizado(boolean anonimizado) {
        this.anonimizado = anonimizado;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
