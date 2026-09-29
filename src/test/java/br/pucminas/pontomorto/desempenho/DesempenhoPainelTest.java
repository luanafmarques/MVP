package br.pucminas.pontomorto.desempenho;

import br.pucminas.pontomorto.config.GeradorHistorico;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.TipoVeiculo;
import br.pucminas.pontomorto.dominio.Veiculo;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.PontoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.repositorio.VeiculoRepository;
import br.pucminas.pontomorto.servico.DashboardService;
import br.pucminas.pontomorto.servico.ParametroService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF03: o painel responde em menos de 3 segundos para consultas de até 12 meses.
 * Gera 12 meses de roteiros para 25 motoristas (cerca de 7 mil roteiros e 45 mil pontos) e mede os três recortes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RNF03 - desempenho do painel com 12 meses de dados")
class DesempenhoPainelTest {

    private static final long LIMITE_MS = 3000;

    @Autowired
    private GeradorHistorico gerador;
    @Autowired
    private MotoristaRepository motoristas;
    @Autowired
    private VeiculoRepository veiculos;
    @Autowired
    private RoteiroRepository roteiros;
    @Autowired
    private PontoRepository pontos;
    @Autowired
    private ParametroService parametros;
    @Autowired
    private DashboardService painel;
    @Autowired
    private Clock relogio;

    @Test
    @DisplayName("dia, mês e período de 12 meses respondem em menos de 3 segundos")
    void painelRapido() {
        LocalDate fim = LocalDate.now(relogio).minusYears(3); // longe dos dados de exemplo
        LocalDate inicio = fim.minusMonths(12).plusDays(1);
        List<Motorista> frota = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Motorista m = new Motorista("Motorista de carga " + i, null, null);
            m.setVeiculo(veiculos.save(new Veiculo(String.format("TST%04d", i), TipoVeiculo.MOTO, "Teste", new BigDecimal("35"))));
            frota.add(motoristas.save(m));
        }
        int criados = gerador.gerar(frota, inicio, fim, parametros.obter(), new Random(42));
        assertThat(criados).isGreaterThan(6000);
        System.out.printf("RNF03: %d roteiros e %d pontos no banco de teste%n", roteiros.count(), pontos.count());

        long t0 = System.nanoTime();
        DashboardService.RecortePeriodo periodo = painel.periodo(inicio, fim, null, null);
        long msPeriodo = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        DashboardService.RecorteMes mes = painel.mes(YearMonth.from(fim.minusMonths(1)), null, null);
        long msMes = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        DashboardService.RecorteDia dia = painel.dia(fim.minusDays(3), null, null);
        long msDia = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("RNF03: período de 12 meses %d ms · mês %d ms · dia %d ms%n", msPeriodo, msMes, msDia);
        assertThat(periodo.indicadores().roteiros()).isGreaterThan(6000);
        assertThat(periodo.meses()).hasSizeGreaterThanOrEqualTo(12);
        assertThat(mes.dias()).isNotEmpty();
        assertThat(dia.indicadores()).isNotNull();
        assertThat(msPeriodo).isLessThan(LIMITE_MS);
        assertThat(msMes).isLessThan(LIMITE_MS);
        assertThat(msDia).isLessThan(LIMITE_MS);
    }
}
