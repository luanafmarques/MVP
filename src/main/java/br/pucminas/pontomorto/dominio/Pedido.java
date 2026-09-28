package br.pucminas.pontomorto.dominio;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/** Pedido de entrega. Ao montar o roteiro, vira (ou entra em) um ponto; vários pedidos podem dividir o mesmo ponto. */
@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pedido_seq")
    @SequenceGenerator(name = "pedido_seq", sequenceName = "pedido_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String numero;

    @Column(nullable = false, length = 120)
    private String cliente;

    @Column(name = "endereco_entrega", nullable = false, length = 255)
    private String enderecoEntrega;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Column(length = 500)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status = StatusPedido.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gerente_id")
    private Gerente gerente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ponto_id")
    private Ponto ponto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Pedido() {
    }

    public Pedido(String numero, String cliente, String enderecoEntrega, LocalDate dataPrevista, LocalDateTime criadoEm) {
        this.numero = numero;
        this.cliente = cliente;
        this.enderecoEntrega = enderecoEntrega;
        this.dataPrevista = dataPrevista;
        this.criadoEm = criadoEm;
    }

    /**
     * Chave usada para juntar pedidos no mesmo ponto: endereço sem acentos, sem pontuação,
     * em minúsculas e com espaços simples. "Rua Peru, 55" e "rua peru 55" caem no mesmo ponto.
     */
    public static String chaveDeEndereco(String endereco) {
        if (endereco == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(endereco, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    public void associarAoPonto(Ponto ponto) {
        this.ponto = ponto;
        this.status = StatusPedido.EM_ROTEIRO;
        ponto.getPedidos().add(this);
    }

    public void liberarDoRoteiro() {
        this.ponto = null;
        this.status = StatusPedido.PENDENTE;
    }

    public boolean temCoordenadas() {
        return latitude != null && longitude != null;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public String getEnderecoEntrega() {
        return enderecoEntrega;
    }

    public void setEnderecoEntrega(String enderecoEntrega) {
        this.enderecoEntrega = enderecoEntrega;
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

    public LocalDate getDataPrevista() {
        return dataPrevista;
    }

    public void setDataPrevista(LocalDate dataPrevista) {
        this.dataPrevista = dataPrevista;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public Gerente getGerente() {
        return gerente;
    }

    public void setGerente(Gerente gerente) {
        this.gerente = gerente;
    }

    public Ponto getPonto() {
        return ponto;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
