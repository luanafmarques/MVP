package br.pucminas.pontomorto.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Pedido de teste grátis feito pela landing page. */
@Entity
@Table(name = "solicitacao_teste")
public class SolicitacaoTeste {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "solicitacao_teste_seq")
    @SequenceGenerator(name = "solicitacao_teste_seq", sequenceName = "solicitacao_teste_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 120)
    private String empresa;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(name = "tamanho_frota")
    private Integer tamanhoFrota;

    @Column(nullable = false)
    private boolean consentimento;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected SolicitacaoTeste() {
    }

    public SolicitacaoTeste(String nome, String empresa, String email, String telefone, Integer tamanhoFrota,
                            boolean consentimento, LocalDateTime criadoEm) {
        this.nome = nome;
        this.empresa = empresa;
        this.email = email;
        this.telefone = telefone;
        this.tamanhoFrota = tamanhoFrota;
        this.consentimento = consentimento;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmpresa() {
        return empresa;
    }

    public String getEmail() {
        return email;
    }
}
