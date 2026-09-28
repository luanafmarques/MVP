package br.pucminas.pontomorto.regras;

import br.pucminas.pontomorto.regras.CalculadoraTempoParado.Parada;
import br.pucminas.pontomorto.regras.CalculadoraTempoParado.ResultadoParada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Regras do tempo parado (RN01 a RN04, RF10)")
class CalculadoraTempoParadoTest {

    private static final ParametrosTempo PADRAO = ParametrosTempo.padrao();
    private static final LocalDateTime DIA = LocalDateTime.of(2026, 9, 21, 0, 0);

    private static LocalDateTime h(int hora, int minuto) {
        return DIA.plusHours(hora).plusMinutes(minuto);
    }

    /** Roteiro A do enunciado: partida, 15 min, 10 min e 50 min (ponto final). */
    static List<Parada> roteiroA() {
        return List.of(
                new Parada(1, h(7, 50), h(8, 0)),    // Seg. Família (partida)
                new Parada(2, h(8, 25), h(8, 40)),   // Rua Peru, 55 — 15 min
                new Parada(3, h(9, 0), h(9, 10)),    // Rua X, 5 — 10 min
                new Parada(4, h(9, 40), h(10, 30))); // Av. João César — 50 min
    }

    @Nested
    @DisplayName("RN01 - ponto de partida")
    class Rn01 {
        @Test
        @DisplayName("não conta tempo parado no ponto de partida, mesmo com chegada e saída registradas")
        void partidaNaoConta() {
            ResultadoParada r = CalculadoraTempoParado.calcularParada(new Parada(1, h(7, 0), h(8, 0)), PADRAO);
            assertThat(r.partida()).isTrue();
            assertThat(r.segundosConsiderados()).isZero();
        }

        @Test
        @DisplayName("começa a contar a partir do 2º ponto")
        void segundoPontoConta() {
            ResultadoParada r = CalculadoraTempoParado.calcularParada(new Parada(2, h(8, 25), h(8, 40)), PADRAO);
            assertThat(r.partida()).isFalse();
            assertThat(r.segundosConsiderados()).isEqualTo(15 * 60);
        }
    }

    @Nested
    @DisplayName("RN02 - tempo parado no ponto")
    class Rn02 {
        @Test
        @DisplayName("é o horário de saída menos o de chegada")
        void saidaMenosChegada() {
            assertThat(CalculadoraTempoParado.tempoParado(h(9, 40), h(10, 30))).isEqualTo(Duration.ofMinutes(50));
        }

        @Test
        @DisplayName("parada que passa da meia-noite usa data e hora completas")
        void passaDaMeiaNoite() {
            LocalDateTime chegada = LocalDateTime.of(2026, 9, 21, 23, 40);
            LocalDateTime saida = LocalDateTime.of(2026, 9, 22, 0, 25);
            assertThat(CalculadoraTempoParado.tempoParado(chegada, saida)).isEqualTo(Duration.ofMinutes(45));
            assertThat(CalculadoraTempoParado.calcularParada(new Parada(3, chegada, saida), PADRAO).segundosConsiderados())
                    .isEqualTo(45 * 60);
        }

        @Test
        @DisplayName("saída antes da chegada é recusada")
        void saidaAntesDaChegada() {
            assertThatThrownBy(() -> CalculadoraTempoParado.tempoParado(h(10, 0), h(9, 59)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("A saída não pode ser antes da chegada.");
            assertThatThrownBy(() -> CalculadoraTempoParado.calcularParada(new Parada(2, h(10, 0), h(9, 0)), PADRAO))
                    .isInstanceOf(RegraNegocioException.class);
        }

        @Test
        @DisplayName("saída igual à chegada dá zero minuto")
        void saidaIgualChegada() {
            assertThat(CalculadoraTempoParado.tempoParado(h(10, 0), h(10, 0))).isZero();
        }

        @Test
        @DisplayName("enquanto falta a saída, o tempo ainda não é calculado")
        void semSaida() {
            ResultadoParada r = CalculadoraTempoParado.calcularParada(new Parada(2, h(8, 25), null), PADRAO);
            assertThat(r.segundosConsiderados()).isNull();
        }
    }

    @Nested
    @DisplayName("RN03 - tempo total do roteiro")
    class Rn03 {
        @Test
        @DisplayName("roteiro A: 15 + 10 + 50 = 75 minutos, sem contar a partida")
        void roteiroA() {
            assertThat(CalculadoraTempoParado.totalDoRoteiro(CalculadoraTempoParadoTest.roteiroA(), PADRAO))
                    .isEqualTo(75 * 60);
        }

        @Test
        @DisplayName("roteiro B: 10 + 5 + 26 = 41 minutos")
        void roteiroB() {
            List<Parada> b = List.of(
                    new Parada(1, null, h(8, 0)),
                    new Parada(2, h(8, 20), h(8, 30)),
                    new Parada(3, h(8, 50), h(8, 55)),
                    new Parada(4, h(9, 20), h(9, 46)));
            assertThat(CalculadoraTempoParado.totalDoRoteiro(b, PADRAO)).isEqualTo(41 * 60);
        }

        @Test
        @DisplayName("roteiro C: 5 + 10 + 30 = 45 minutos")
        void roteiroC() {
            List<Parada> c = List.of(
                    new Parada(1, h(7, 30), h(7, 45)),
                    new Parada(2, h(8, 0), h(8, 5)),
                    new Parada(3, h(8, 30), h(8, 40)),
                    new Parada(4, h(9, 10), h(9, 40)));
            assertThat(CalculadoraTempoParado.totalDoRoteiro(c, PADRAO)).isEqualTo(45 * 60);
        }

        @Test
        @DisplayName("pontos ainda sem saída não entram na soma")
        void pontosIncompletos() {
            List<Parada> parcial = List.of(
                    new Parada(1, null, h(8, 0)),
                    new Parada(2, h(8, 25), h(8, 40)),
                    new Parada(3, h(9, 0), null));
            assertThat(CalculadoraTempoParado.totalDoRoteiro(parcial, PADRAO)).isEqualTo(15 * 60);
        }
    }

    @Nested
    @DisplayName("RN04 - jornada padrão de 8 horas")
    class Rn04 {
        @Test
        @DisplayName("75 minutos parados em uma jornada de 8 h = 15,6%")
        void percentualRoteiroA() {
            assertThat(CalculadoraTempoParado.percentualDaJornada(75 * 60, 1, BigDecimal.valueOf(8)))
                    .isEqualByComparingTo("15.6");
        }

        @Test
        @DisplayName("no período, a base é uma jornada por roteiro")
        void percentualPeriodo() {
            // A + B + C = 161 min em 3 jornadas de 8 h (1440 min) = 11,2%
            assertThat(CalculadoraTempoParado.percentualDaJornada(161 * 60, 3, BigDecimal.valueOf(8)))
                    .isEqualByComparingTo("11.2");
        }

        @Test
        @DisplayName("a jornada vem do parâmetro: com 6 h, os mesmos 75 minutos viram 20,8%")
        void jornadaParametrizada() {
            assertThat(CalculadoraTempoParado.percentualDaJornada(75 * 60, 1, BigDecimal.valueOf(6)))
                    .isEqualByComparingTo("20.8");
        }
    }

    @Nested
    @DisplayName("RF10 - regras parametrizáveis")
    class Rf10 {
        @Test
        @DisplayName("paradas menores que X minutos contam como zero")
        void ignoraParadasCurtas() {
            ParametrosTempo p = new ParametrosTempo(12, 0, BigDecimal.valueOf(8));
            ResultadoParada curta = CalculadoraTempoParado.calcularParada(new Parada(3, h(9, 0), h(9, 10)), p);
            assertThat(curta.ignorada()).isTrue();
            assertThat(curta.segundosConsiderados()).isZero();
            assertThat(curta.segundosBrutos()).isEqualTo(10 * 60);
            // Roteiro A: a parada de 10 min é ignorada -> 15 + 50 = 65 min
            assertThat(CalculadoraTempoParado.totalDoRoteiro(roteiroA(), p)).isEqualTo(65 * 60);
        }

        @Test
        @DisplayName("paradas acima do máximo são limitadas ao máximo e sinalizadas")
        void limitaParadasLongas() {
            ParametrosTempo p = new ParametrosTempo(0, 30, BigDecimal.valueOf(8));
            ResultadoParada longa = CalculadoraTempoParado.calcularParada(new Parada(4, h(9, 40), h(10, 30)), p);
            assertThat(longa.acimaDoMaximo()).isTrue();
            assertThat(longa.segundosConsiderados()).isEqualTo(30 * 60);
            // Roteiro A: 15 + 10 + 30 = 55 min
            assertThat(CalculadoraTempoParado.totalDoRoteiro(roteiroA(), p)).isEqualTo(55 * 60);
        }
    }
}
