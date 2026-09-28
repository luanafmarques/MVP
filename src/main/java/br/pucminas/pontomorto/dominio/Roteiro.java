package br.pucminas.pontomorto.dominio;

import br.pucminas.pontomorto.regras.CalculadoraDistancia;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Roteiro diário de um motorista (RF04).
 * RN05: um único motorista e uma única data (restrição única no banco).
 * RN06: pontos em ordem sequencial 1, 2, 3... mantida pelos métodos desta classe.
 */
@Entity
@Table(name = "roteiro")
public class Roteiro {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "roteiro_seq")
    @SequenceGenerator(name = "roteiro_seq", sequenceName = "roteiro_seq", allocationSize = 50)
    private Long id;

    @Column(name = "data_roteiro", nullable = false)
    private LocalDate data;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motorista_id", nullable = false)
    private Motorista motorista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRoteiro status = StatusRoteiro.PLANEJADO;

    @OneToMany(mappedBy = "roteiro", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<Ponto> pontos = new ArrayList<>();

    @Column(name = "distancia_estimada_km", precision = 8, scale = 2)
    private BigDecimal distanciaEstimadaKm;

    /** Verdadeiro quando a estimativa veio da linha reta (Haversine) porque a API de rotas não respondeu. */
    @Column(name = "distancia_estimada_aproximada", nullable = false)
    private boolean distanciaEstimadaAproximada;

    @Column(name = "km_inicial", precision = 10, scale = 1)
    private BigDecimal kmInicial;

    @Column(name = "km_final", precision = 10, scale = 1)
    private BigDecimal kmFinal;

    @Column(name = "distancia_real_km", precision = 8, scale = 1)
    private BigDecimal distanciaRealKm;

    @Column(name = "tempo_total_parado_seg", nullable = false)
    private long tempoTotalParadoSeg;

    @Column(name = "custo_estimado", precision = 10, scale = 2)
    private BigDecimal custoEstimado;

    // Valores usados no último cálculo do custo: o histórico não muda sozinho quando o preço muda.
    @Column(name = "preco_combustivel_usado", precision = 8, scale = 3)
    private BigDecimal precoCombustivelUsado;

    @Column(name = "km_litro_usado", precision = 6, scale = 2)
    private BigDecimal kmLitroUsado;

    @Column(name = "custo_por_km_usado", precision = 8, scale = 3)
    private BigDecimal custoPorKmUsado;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Roteiro() {
    }

    public Roteiro(LocalDate data, Motorista motorista, LocalDateTime criadoEm) {
        this.data = data;
        this.motorista = motorista;
        this.criadoEm = criadoEm;
    }

    // ---------- RN06: ordem sequencial ----------

    /** Adiciona o ponto no fim do trajeto. */
    public Ponto adicionarPonto(Ponto ponto) {
        ponto.setRoteiro(this);
        pontos.add(ponto);
        renumerar();
        return ponto;
    }

    /** Coloca o ponto na posição informada (1 = primeiro) e renumera os demais. */
    public void moverPara(Ponto ponto, int novaOrdem) {
        if (!pontos.remove(ponto)) {
            throw new RegraNegocioException("Esse ponto não pertence ao roteiro.");
        }
        int indice = Math.max(0, Math.min(novaOrdem - 1, pontos.size()));
        pontos.add(indice, ponto);
        renumerar();
    }

    /** Sobe (-1) ou desce (+1) o ponto uma posição. */
    public void mover(Ponto ponto, int deslocamento) {
        int atual = pontos.indexOf(ponto);
        if (atual < 0) {
            throw new RegraNegocioException("Esse ponto não pertence ao roteiro.");
        }
        int destino = atual + deslocamento;
        if (destino < 0 || destino >= pontos.size()) {
            return;
        }
        Collections.swap(pontos, atual, destino);
        renumerar();
    }

    public void removerPonto(Ponto ponto) {
        if (!pontos.remove(ponto)) {
            throw new RegraNegocioException("Esse ponto não pertence ao roteiro.");
        }
        renumerar();
    }

    /** Garante a sequência 1, 2, 3... sem buracos, na ordem da lista. */
    public void renumerar() {
        for (int i = 0; i < pontos.size(); i++) {
            pontos.get(i).setOrdem(i + 1);
        }
    }

    public Optional<Ponto> getPartida() {
        return pontos.isEmpty() ? Optional.empty() : Optional.of(pontos.get(0));
    }

    /** Próximo ponto sem saída registrada: é nele que o motorista está ou para onde está indo. */
    public Optional<Ponto> getPontoAtual() {
        return pontos.stream().filter(p -> p.getSaida() == null).findFirst();
    }

    public Ponto buscarPonto(Long pontoId) {
        return pontos.stream()
                .filter(p -> p.getId() != null && p.getId().equals(pontoId))
                .findFirst()
                .orElseThrow(() -> new RegraNegocioException("Ponto não encontrado neste roteiro."));
    }

    public boolean isColetaIniciada() {
        return pontos.stream().anyMatch(p -> p.getChegada() != null || p.getSaida() != null);
    }

    // ---------- Distâncias ----------

    public BigDecimal getDistanciaParaCusto() {
        return CalculadoraDistancia.distanciaParaCusto(distanciaRealKm, distanciaEstimadaKm);
    }

    public BigDecimal getDiferencaDistanciaKm() {
        return CalculadoraDistancia.diferenca(distanciaRealKm, distanciaEstimadaKm);
    }

    public long getTempoTotalParadoMin() {
        return Math.round(tempoTotalParadoSeg / 60.0);
    }

    // ---------- Getters e setters ----------

    public Long getId() {
        return id;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Motorista getMotorista() {
        return motorista;
    }

    public StatusRoteiro getStatus() {
        return status;
    }

    public void setStatus(StatusRoteiro status) {
        this.status = status;
    }

    public List<Ponto> getPontos() {
        return pontos;
    }

    public BigDecimal getDistanciaEstimadaKm() {
        return distanciaEstimadaKm;
    }

    public boolean isDistanciaEstimadaAproximada() {
        return distanciaEstimadaAproximada;
    }

    public void definirDistanciaEstimada(BigDecimal km, boolean aproximada) {
        this.distanciaEstimadaKm = km;
        this.distanciaEstimadaAproximada = aproximada;
    }

    public BigDecimal getKmInicial() {
        return kmInicial;
    }

    public BigDecimal getKmFinal() {
        return kmFinal;
    }

    /** Grava o hodômetro e recalcula a distância real (km final menos km inicial). */
    public void definirHodometro(BigDecimal kmInicial, BigDecimal kmFinal) {
        CalculadoraDistancia.validarHodometro(kmInicial, kmFinal);
        this.kmInicial = kmInicial;
        this.kmFinal = kmFinal;
        this.distanciaRealKm = CalculadoraDistancia.distanciaReal(kmInicial, kmFinal);
    }

    public BigDecimal getDistanciaRealKm() {
        return distanciaRealKm;
    }

    public long getTempoTotalParadoSeg() {
        return tempoTotalParadoSeg;
    }

    public void setTempoTotalParadoSeg(long tempoTotalParadoSeg) {
        this.tempoTotalParadoSeg = tempoTotalParadoSeg;
    }

    public BigDecimal getCustoEstimado() {
        return custoEstimado;
    }

    public void definirCusto(BigDecimal custo, BigDecimal precoCombustivel, BigDecimal kmLitro, BigDecimal custoPorKm) {
        this.custoEstimado = custo;
        this.precoCombustivelUsado = precoCombustivel;
        this.kmLitroUsado = kmLitro;
        this.custoPorKmUsado = custoPorKm;
    }

    public BigDecimal getPrecoCombustivelUsado() {
        return precoCombustivelUsado;
    }

    public BigDecimal getKmLitroUsado() {
        return kmLitroUsado;
    }

    public BigDecimal getCustoPorKmUsado() {
        return custoPorKmUsado;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
