package br.pucminas.pontomorto.regras;

import br.pucminas.pontomorto.regras.CalculadoraDistancia.Coordenada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Distância do roteiro (estimada e real)")
class CalculadoraDistanciaTest {

    private static final Coordenada PRACA_SETE = new Coordenada(-19.9191, -43.9386);
    private static final Coordenada PUC_CORACAO = new Coordenada(-19.9227, -43.9925);

    @Test
    @DisplayName("Haversine: Praça Sete até a PUC Coração Eucarístico fica perto de 5,7 km em linha reta")
    void haversine() {
        assertThat(CalculadoraDistancia.haversineKm(PRACA_SETE, PUC_CORACAO)).isCloseTo(5.66, within(0.1));
    }

    @Test
    @DisplayName("linha reta soma cada ponto até o seguinte, na ordem")
    void somaEmOrdem() {
        BigDecimal idaEVolta = CalculadoraDistancia.linhaRetaKm(List.of(PRACA_SETE, PUC_CORACAO, PRACA_SETE));
        assertThat(idaEVolta.doubleValue()).isCloseTo(11.32, within(0.2));
        assertThat(CalculadoraDistancia.linhaRetaKm(List.of(PRACA_SETE))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("distância real = km final menos km inicial")
    void distanciaReal() {
        assertThat(CalculadoraDistancia.distanciaReal(new BigDecimal("15230.5"), new BigDecimal("15268.9")))
                .isEqualByComparingTo("38.4");
    }

    @Test
    @DisplayName("km final precisa ser maior que o km inicial")
    void kmFinalMenor() {
        assertThatThrownBy(() -> CalculadoraDistancia.distanciaReal(new BigDecimal("100"), new BigDecimal("90")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O km final precisa ser maior que o km inicial.");
        assertThatThrownBy(() -> CalculadoraDistancia.distanciaReal(new BigDecimal("100"), new BigDecimal("100")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("o custo usa a distância real quando existe e a estimada enquanto o roteiro não termina")
    void distanciaParaCusto() {
        assertThat(CalculadoraDistancia.distanciaParaCusto(new BigDecimal("38.4"), new BigDecimal("30.0")))
                .isEqualByComparingTo("38.4");
        assertThat(CalculadoraDistancia.distanciaParaCusto(null, new BigDecimal("30.0")))
                .isEqualByComparingTo("30.0");
    }

    @Test
    @DisplayName("diferença entre real e estimada")
    void diferenca() {
        assertThat(CalculadoraDistancia.diferenca(new BigDecimal("38.4"), new BigDecimal("30.0")))
                .isEqualByComparingTo("8.4");
        assertThat(CalculadoraDistancia.diferenca(null, new BigDecimal("30.0"))).isNull();
    }
}
