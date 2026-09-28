package br.pucminas.pontomorto.regras;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RN07 / RF11 - custo do trajeto")
class CalculadoraCustoTest {

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("custo = (distância ÷ km/l × preço) + (distância × custo por km)")
    void formulaDoCusto() {
        // 36 km, carro de 12 km/l, gasolina a R$ 6,20, custo de R$ 0,45 por km
        // combustível: 36 / 12 × 6,20 = 18,60 ; rodagem: 36 × 0,45 = 16,20 ; total = 34,80
        CalculadoraCusto.Composicao c = CalculadoraCusto.composicao(d("36"), d("12"), d("6.20"), d("0.45"));
        assertThat(c.litros()).isEqualByComparingTo("3.00");
        assertThat(c.custoCombustivel()).isEqualByComparingTo("18.60");
        assertThat(c.custoPorKmRodado()).isEqualByComparingTo("16.20");
        assertThat(c.total()).isEqualByComparingTo("34.80");
    }

    @Test
    @DisplayName("usa o rendimento do veículo: a moto (35 km/l) gasta menos combustível no mesmo trajeto")
    void rendimentoDoVeiculo() {
        BigDecimal carro = CalculadoraCusto.custoDoTrajeto(d("35"), d("12"), d("6.20"), d("0"));
        BigDecimal moto = CalculadoraCusto.custoDoTrajeto(d("35"), d("35"), d("6.20"), d("0"));
        assertThat(moto).isEqualByComparingTo("6.20");
        assertThat(carro).isGreaterThan(moto);
    }

    @Test
    @DisplayName("distância zero dá custo zero")
    void distanciaZero() {
        assertThat(CalculadoraCusto.custoDoTrajeto(BigDecimal.ZERO, d("12"), d("6.20"), d("0.45")))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("km por litro precisa ser maior que zero")
    void rendimentoInvalido() {
        assertThatThrownBy(() -> CalculadoraCusto.custoDoTrajeto(d("10"), d("0"), d("6.20"), d("0.45")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("valores negativos são recusados")
    void negativos() {
        assertThatThrownBy(() -> CalculadoraCusto.custoDoTrajeto(d("-1"), d("12"), d("6.20"), d("0.45")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> CalculadoraCusto.custoDoTrajeto(d("10"), d("12"), d("-6.20"), d("0.45")))
                .isInstanceOf(RegraNegocioException.class);
    }
}
