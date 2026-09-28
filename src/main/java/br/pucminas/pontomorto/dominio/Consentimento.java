package br.pucminas.pontomorto.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Aceite do aviso de privacidade (RNF06 / LGPD). Guarda só a versão do aviso e quando foi aceito. */
@Entity
@Table(name = "consentimento")
public class Consentimento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "consentimento_seq")
    @SequenceGenerator(name = "consentimento_seq", sequenceName = "consentimento_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "versao_aviso", nullable = false, length = 10)
    private String versaoAviso;

    @Column(name = "aceito_em", nullable = false)
    private LocalDateTime aceitoEm;

    protected Consentimento() {
    }

    public Consentimento(Usuario usuario, String versaoAviso, LocalDateTime aceitoEm) {
        this.usuario = usuario;
        this.versaoAviso = versaoAviso;
        this.aceitoEm = aceitoEm;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getVersaoAviso() {
        return versaoAviso;
    }

    public LocalDateTime getAceitoEm() {
        return aceitoEm;
    }
}
