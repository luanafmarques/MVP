package br.pucminas.pontomorto.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Registro de auditoria (RNF05): quem alterou, quando, qual campo, valor antigo e valor novo. */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    public enum Acao {
        CRIACAO("Criação"), ALTERACAO("Alteração"), EXCLUSAO("Exclusão");

        private final String rotulo;

        Acao(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auditoria_seq")
    @SequenceGenerator(name = "auditoria_seq", sequenceName = "auditoria_seq", allocationSize = 50)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false, length = 120)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Acao acao;

    @Column(nullable = false, length = 40)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(length = 255)
    private String descricao;

    @Column(length = 60)
    private String campo;

    @Column(name = "valor_antigo", length = 500)
    private String valorAntigo;

    @Column(name = "valor_novo", length = 500)
    private String valorNovo;

    @Column(length = 255)
    private String motivo;

    @Column(name = "gerente_id")
    private Long gerenteId;

    protected Auditoria() {
    }

    public Auditoria(LocalDateTime dataHora, String usuario, Acao acao, String entidade, Long entidadeId,
                     String descricao, String campo, String valorAntigo, String valorNovo, String motivo,
                     Long gerenteId) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.descricao = cortar(descricao, 255);
        this.campo = campo;
        this.valorAntigo = cortar(valorAntigo, 500);
        this.valorNovo = cortar(valorNovo, 500);
        this.motivo = cortar(motivo, 255);
        this.gerenteId = gerenteId;
    }

    private static String cortar(String texto, int limite) {
        return texto == null || texto.length() <= limite ? texto : texto.substring(0, limite);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public Acao getAcao() {
        return acao;
    }

    public String getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAntigo() {
        return valorAntigo;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getGerenteId() {
        return gerenteId;
    }
}
