package br.pucminas.pontomorto.regras;

import java.math.BigDecimal;

/**
 * Regras de cálculo do tempo parado que o administrador pode mudar pela tela (RF10).
 *
 * @param ignorarParadaMenorQueMin paradas menores que isso contam como zero (0 = desligado)
 * @param tempoMaximoParadaMin     paradas maiores que isso são limitadas a esse valor e sinalizadas (0 = sem limite)
 * @param jornadaHoras             jornada padrão por dia, base do percentual de tempo parado (RN04)
 */
public record ParametrosTempo(int ignorarParadaMenorQueMin, int tempoMaximoParadaMin, BigDecimal jornadaHoras) {

    /** Jornada padrão de 8 horas, sem regras extras: aplica exatamente RN01 a RN04. */
    public static ParametrosTempo padrao() {
        return new ParametrosTempo(0, 0, BigDecimal.valueOf(8));
    }
}
