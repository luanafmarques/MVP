package br.pucminas.pontomorto.regras;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cálculo do tempo parado.
 * <ul>
 *   <li>RN01: o ponto de partida (ordem 1) não conta tempo parado.</li>
 *   <li>RN02: tempo parado no ponto = saída menos chegada (data e hora completas, então vale para paradas
 *       que passam da meia-noite).</li>
 *   <li>RN03: tempo total do roteiro = soma dos pontos, sem o ponto de partida.</li>
 *   <li>RN04: percentual sobre a jornada padrão (8 h por dia).</li>
 *   <li>RF10: ignorar paradas curtas e limitar paradas longas, conforme os parâmetros.</li>
 * </ul>
 */
public final class CalculadoraTempoParado {

    public static final int ORDEM_PARTIDA = 1;

    private CalculadoraTempoParado() {
    }

    /** Uma parada registrada: posição no roteiro e horários (podem estar vazios se ainda não aconteceram). */
    public record Parada(int ordem, LocalDateTime chegada, LocalDateTime saida) {
    }

    /**
     * Resultado do cálculo de um ponto.
     *
     * @param segundosConsiderados tempo que entra nos indicadores; {@code null} enquanto falta chegada ou saída
     * @param segundosBrutos       saída menos chegada, sem regras extras; {@code null} enquanto incompleto
     */
    public record ResultadoParada(Long segundosConsiderados, Long segundosBrutos,
                                  boolean partida, boolean ignorada, boolean acimaDoMaximo) {
    }

    /** RN02: saída menos chegada. Não aceita saída antes da chegada. */
    public static Duration tempoParado(LocalDateTime chegada, LocalDateTime saida) {
        if (chegada == null || saida == null) {
            throw new RegraNegocioException("Informe a chegada e a saída para calcular o tempo parado.");
        }
        validarHorarios(chegada, saida);
        return Duration.between(chegada, saida);
    }

    /** Validação usada em toda gravação de horário: a saída não pode ser antes da chegada. */
    public static void validarHorarios(LocalDateTime chegada, LocalDateTime saida) {
        if (chegada != null && saida != null && saida.isBefore(chegada)) {
            throw new RegraNegocioException("A saída não pode ser antes da chegada.");
        }
    }

    public static ResultadoParada calcularParada(Parada parada, ParametrosTempo parametros) {
        validarHorarios(parada.chegada(), parada.saida());
        boolean partida = parada.ordem() == ORDEM_PARTIDA;
        Long bruto = (parada.chegada() != null && parada.saida() != null)
                ? Duration.between(parada.chegada(), parada.saida()).toSeconds()
                : null;

        if (partida) {
            // RN01: o ponto de partida nunca conta, mesmo que tenha horários registrados.
            return new ResultadoParada(0L, bruto, true, false, false);
        }
        if (bruto == null) {
            return new ResultadoParada(null, null, false, false, false);
        }

        long considerado = bruto;
        boolean ignorada = false;
        boolean acimaDoMaximo = false;
        if (parametros.ignorarParadaMenorQueMin() > 0 && bruto < parametros.ignorarParadaMenorQueMin() * 60L) {
            considerado = 0;
            ignorada = true;
        } else if (parametros.tempoMaximoParadaMin() > 0 && bruto > parametros.tempoMaximoParadaMin() * 60L) {
            considerado = parametros.tempoMaximoParadaMin() * 60L;
            acimaDoMaximo = true;
        }
        return new ResultadoParada(considerado, bruto, false, ignorada, acimaDoMaximo);
    }

    /** RN03: soma do tempo considerado de todos os pontos, sem o ponto de partida. */
    public static long totalDoRoteiro(List<Parada> paradas, ParametrosTempo parametros) {
        long total = 0;
        for (Parada parada : paradas) {
            ResultadoParada resultado = calcularParada(parada, parametros);
            if (!resultado.partida() && resultado.segundosConsiderados() != null) {
                total += resultado.segundosConsiderados();
            }
        }
        return total;
    }

    /**
     * RN04: percentual do tempo parado sobre a jornada. Para um período, a base é a jornada de cada roteiro
     * (quantidade de roteiros × jornada padrão).
     */
    public static BigDecimal percentualDaJornada(long segundosParados, long quantidadeRoteiros, BigDecimal jornadaHoras) {
        if (quantidadeRoteiros <= 0 || jornadaHoras == null || jornadaHoras.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal base = jornadaHoras.multiply(BigDecimal.valueOf(3600)).multiply(BigDecimal.valueOf(quantidadeRoteiros));
        return BigDecimal.valueOf(segundosParados)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 1, RoundingMode.HALF_UP);
    }
}
