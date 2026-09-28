package br.pucminas.pontomorto.dominio;

import br.pucminas.pontomorto.regras.ParametrosTempo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Parâmetros de custo (RF09) e de cálculo do tempo parado (RF10). Existe uma única linha (id = 1),
 * alterada pela tela de parâmetros, sem mudar código.
 */
@Entity
@Table(name = "parametro")
public class Parametro {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    @Column(name = "preco_combustivel", nullable = false, precision = 8, scale = 3)
    private BigDecimal precoCombustivel;

    /** Usado quando o veículo do motorista não tem rendimento informado. */
    @Column(name = "km_litro_padrao", nullable = false, precision = 6, scale = 2)
    private BigDecimal kmLitroPadrao;

    @Column(name = "custo_por_km", nullable = false, precision = 8, scale = 3)
    private BigDecimal custoPorKm;

    @Column(name = "jornada_horas", nullable = false, precision = 4, scale = 2)
    private BigDecimal jornadaHoras;

    @Column(name = "ignorar_parada_menor_que_min", nullable = false)
    private int ignorarParadaMenorQueMin;

    @Column(name = "tempo_maximo_parada_min", nullable = false)
    private int tempoMaximoParadaMin;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    protected Parametro() {
    }

    public ParametrosTempo paraCalculoDeTempo() {
        return new ParametrosTempo(ignorarParadaMenorQueMin, tempoMaximoParadaMin, jornadaHoras);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPrecoCombustivel() {
        return precoCombustivel;
    }

    public void setPrecoCombustivel(BigDecimal precoCombustivel) {
        this.precoCombustivel = precoCombustivel;
    }

    public BigDecimal getKmLitroPadrao() {
        return kmLitroPadrao;
    }

    public void setKmLitroPadrao(BigDecimal kmLitroPadrao) {
        this.kmLitroPadrao = kmLitroPadrao;
    }

    public BigDecimal getCustoPorKm() {
        return custoPorKm;
    }

    public void setCustoPorKm(BigDecimal custoPorKm) {
        this.custoPorKm = custoPorKm;
    }

    public BigDecimal getJornadaHoras() {
        return jornadaHoras;
    }

    public void setJornadaHoras(BigDecimal jornadaHoras) {
        this.jornadaHoras = jornadaHoras;
    }

    public int getIgnorarParadaMenorQueMin() {
        return ignorarParadaMenorQueMin;
    }

    public void setIgnorarParadaMenorQueMin(int ignorarParadaMenorQueMin) {
        this.ignorarParadaMenorQueMin = ignorarParadaMenorQueMin;
    }

    public int getTempoMaximoParadaMin() {
        return tempoMaximoParadaMin;
    }

    public void setTempoMaximoParadaMin(int tempoMaximoParadaMin) {
        this.tempoMaximoParadaMin = tempoMaximoParadaMin;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public void registrarAtualizacao(String usuario, LocalDateTime quando) {
        this.atualizadoPor = usuario;
        this.atualizadoEm = quando;
    }
}
