package br.pucminas.pontomorto.regras;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * RN07 / RF11: custo estimado do trajeto.
 * <pre>custo = (distância ÷ km por litro × preço do combustível) + (distância × custo por km)</pre>
 */
public final class CalculadoraCusto {

    private CalculadoraCusto() {
    }

    public record Composicao(BigDecimal litros, BigDecimal custoCombustivel, BigDecimal custoPorKmRodado, BigDecimal total) {
    }

    public static BigDecimal custoDoTrajeto(BigDecimal distanciaKm, BigDecimal kmPorLitro,
                                            BigDecimal precoCombustivel, BigDecimal custoPorKm) {
        return composicao(distanciaKm, kmPorLitro, precoCombustivel, custoPorKm).total();
    }

    public static Composicao composicao(BigDecimal distanciaKm, BigDecimal kmPorLitro,
                                        BigDecimal precoCombustivel, BigDecimal custoPorKm) {
        if (kmPorLitro == null || kmPorLitro.signum() <= 0) {
            throw new RegraNegocioException("O rendimento do veículo (km por litro) precisa ser maior que zero.");
        }
        BigDecimal distancia = naoNegativo(distanciaKm, "A distância não pode ser negativa.");
        BigDecimal preco = naoNegativo(precoCombustivel, "O preço do combustível não pode ser negativo.");
        BigDecimal custoKm = naoNegativo(custoPorKm, "O custo por km não pode ser negativo.");

        BigDecimal litros = distancia.divide(kmPorLitro, 6, RoundingMode.HALF_UP);
        BigDecimal custoCombustivel = litros.multiply(preco);
        BigDecimal custoRodado = distancia.multiply(custoKm);
        return new Composicao(
                litros.setScale(2, RoundingMode.HALF_UP),
                custoCombustivel.setScale(2, RoundingMode.HALF_UP),
                custoRodado.setScale(2, RoundingMode.HALF_UP),
                custoCombustivel.add(custoRodado).setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal naoNegativo(BigDecimal valor, String mensagem) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor.signum() < 0) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }
}
