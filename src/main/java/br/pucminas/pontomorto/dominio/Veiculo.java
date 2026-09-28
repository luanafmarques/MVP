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

import java.math.BigDecimal;

/** Veículo do motorista. O rendimento km/l é usado no custo do trajeto (RN07). */
@Entity
@Table(name = "veiculo")
public class Veiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "veiculo_seq")
    @SequenceGenerator(name = "veiculo_seq", sequenceName = "veiculo_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoVeiculo tipo;

    @Column(length = 80)
    private String modelo;

    @Column(name = "rendimento_km_litro", nullable = false, precision = 6, scale = 2)
    private BigDecimal rendimentoKmLitro;

    protected Veiculo() {
    }

    public Veiculo(String placa, TipoVeiculo tipo, String modelo, BigDecimal rendimentoKmLitro) {
        this.placa = placa;
        this.tipo = tipo;
        this.modelo = modelo;
        this.rendimentoKmLitro = rendimentoKmLitro;
    }

    public Long getId() {
        return id;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public TipoVeiculo getTipo() {
        return tipo;
    }

    public void setTipo(TipoVeiculo tipo) {
        this.tipo = tipo;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public BigDecimal getRendimentoKmLitro() {
        return rendimentoKmLitro;
    }

    public void setRendimentoKmLitro(BigDecimal rendimentoKmLitro) {
        this.rendimentoKmLitro = rendimentoKmLitro;
    }

    public String getDescricao() {
        return tipo.getRotulo() + (modelo != null && !modelo.isBlank() ? " " + modelo : "") + " · " + placa;
    }
}
