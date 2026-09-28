package br.pucminas.pontomorto.dominio;

import br.pucminas.pontomorto.regras.CalculadoraTempoParado;
import br.pucminas.pontomorto.regras.CalculadoraTempoParado.ResultadoParada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ponto do roteiro: uma parada, com endereço, ordem e horários de chegada e saída. */
@Entity
@Table(name = "ponto")
public class Ponto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ponto_seq")
    @SequenceGenerator(name = "ponto_seq", sequenceName = "ponto_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roteiro_id", nullable = false)
    private Roteiro roteiro;

    /** RN06: posição no trajeto do dia (1, 2, 3...). */
    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false, length = 255)
    private String endereco;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    private LocalDateTime chegada;

    private LocalDateTime saida;

    /** Tempo que entra nos indicadores, já com as regras do RF10. */
    @Column(name = "tempo_parado_seg")
    private Long tempoParadoSeg;

    /** Saída menos chegada, sem regras extras (RN02). */
    @Column(name = "tempo_bruto_seg")
    private Long tempoBrutoSeg;

    @Column(nullable = false)
    private boolean ignorada;

    @Column(name = "acima_do_maximo", nullable = false)
    private boolean acimaDoMaximo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_id")
    private Local local;

    @OneToMany(mappedBy = "ponto")
    @OrderBy("numero")
    private List<Pedido> pedidos = new ArrayList<>();

    protected Ponto() {
    }

    public Ponto(String endereco, BigDecimal latitude, BigDecimal longitude) {
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Ponto deLocal(Local local) {
        Ponto ponto = new Ponto(local.getEndereco(), local.getLatitude(), local.getLongitude());
        ponto.local = local;
        return ponto;
    }

    public boolean isPartida() {
        return ordem == CalculadoraTempoParado.ORDEM_PARTIDA;
    }

    public boolean isConcluido() {
        return saida != null;
    }

    public boolean temCoordenadas() {
        return latitude != null && longitude != null;
    }

    /** Aplica o resultado do cálculo (RN01, RN02, RF10). */
    public void aplicar(ResultadoParada resultado) {
        this.tempoParadoSeg = resultado.segundosConsiderados();
        this.tempoBrutoSeg = resultado.segundosBrutos();
        this.ignorada = resultado.ignorada();
        this.acimaDoMaximo = resultado.acimaDoMaximo();
    }

    public CalculadoraTempoParado.Parada comoParada() {
        return new CalculadoraTempoParado.Parada(ordem, chegada, saida);
    }

    /** Tempo parado em minutos, para exibição (arredondado). */
    public Long getTempoParadoMin() {
        return tempoParadoSeg == null ? null : Math.round(tempoParadoSeg / 60.0);
    }

    public Long getTempoBrutoMin() {
        return tempoBrutoSeg == null ? null : Math.round(tempoBrutoSeg / 60.0);
    }

    public Long getId() {
        return id;
    }

    public Roteiro getRoteiro() {
        return roteiro;
    }

    void setRoteiro(Roteiro roteiro) {
        this.roteiro = roteiro;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public LocalDateTime getChegada() {
        return chegada;
    }

    public void setChegada(LocalDateTime chegada) {
        this.chegada = chegada;
    }

    public LocalDateTime getSaida() {
        return saida;
    }

    public void setSaida(LocalDateTime saida) {
        this.saida = saida;
    }

    public Long getTempoParadoSeg() {
        return tempoParadoSeg;
    }

    public Long getTempoBrutoSeg() {
        return tempoBrutoSeg;
    }

    public boolean isIgnorada() {
        return ignorada;
    }

    public boolean isAcimaDoMaximo() {
        return acimaDoMaximo;
    }

    public Local getLocal() {
        return local;
    }

    public List<Pedido> getPedidos() {
        return pedidos;
    }
}
