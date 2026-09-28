package br.pucminas.pontomorto.repositorio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linhas das consultas agregadas do painel (RF08). Somas feitas no banco para responder rápido (RNF03). */
public final class Agregados {

    private Agregados() {
    }

    /** Totais de roteiros agrupados por data. */
    public record TotalPorData(LocalDate data, Long roteiros, Long segundosParados, BigDecimal custo,
                               BigDecimal distanciaEstimada, BigDecimal distanciaReal,
                               BigDecimal distanciaEstimadaComReal) {
    }

    /** Totais de roteiros agrupados por mês. */
    public record TotalPorMes(Integer ano, Integer mes, Long roteiros, Long segundosParados, BigDecimal custo,
                              BigDecimal distanciaEstimada, BigDecimal distanciaReal,
                              BigDecimal distanciaEstimadaComReal) {
    }

    /** Totais de roteiros agrupados por motorista. */
    public record TotalPorMotorista(Long motoristaId, String nome, Long roteiros, Long segundosParados,
                                    BigDecimal custo) {
    }

    /** Ranking de endereços com mais tempo parado. */
    public record TempoPorEndereco(String endereco, Long paradas, Long segundosParados, Long maiorParadaSeg) {
    }
}
