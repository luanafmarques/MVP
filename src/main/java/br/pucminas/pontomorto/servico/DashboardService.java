package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.regras.CalculadoraTempoParado;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.Agregados;
import br.pucminas.pontomorto.repositorio.PontoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Painel (RF08): tempo parado por dia, por mês e por período, com ranking de endereços e indicadores
 * (total parado, média por roteiro, % da jornada, custo estimado e distâncias estimada × real).
 * Todas as somas são feitas no banco (consultas agregadas com índices) para responder rápido (RNF03).
 * Só entram roteiros com coleta (em andamento ou concluídos).
 */
@Service
public class DashboardService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final int MAXIMO_DIAS_PERIODO = 366;

    /**
     * Indicadores do recorte. {@code custoPorKm} = custo total ÷ km percorridos (real ou, sem hodômetro, estimado);
     * {@code kmLitroMedio} = km percorridos ÷ litros estimados (km ÷ km/l de cada roteiro). Nulos quando não há km.
     */
    public record Indicadores(long roteiros, long totalParadoMin, long mediaPorRoteiroMin, BigDecimal percentualJornada,
                              BigDecimal custoTotal, BigDecimal distanciaEstimadaKm, BigDecimal distanciaRealKm,
                              BigDecimal diferencaKm, BigDecimal jornadaHoras, BigDecimal kmPercorrido,
                              BigDecimal custoPorKm, BigDecimal litros, BigDecimal kmLitroMedio) {
    }

    /** Uma barra do gráfico (um dia ou um mês). */
    public record ItemSerie(String chave, String rotulo, long roteiros, long minutosParados,
                            BigDecimal percentualJornada, BigDecimal custo) {
    }

    /** Uma parada do dia, sempre com endereço e horários registrados (critério de aceitação 3). */
    public record ParadaDoDia(Long roteiroId, String motorista, int ordem, String endereco, String chegada, String saida,
                              long minutos, boolean acimaDoMaximo) {
    }

    public record ItemRanking(int posicao, String endereco, long paradas, long minutos, long mediaMin, long maiorParadaMin) {
    }

    public record ItemMotorista(Long motoristaId, String nome, long roteiros, long minutosParados, long mediaMin,
                                BigDecimal percentualJornada, BigDecimal custo) {
    }

    public record RecorteDia(LocalDate data, String titulo, Indicadores indicadores, List<ParadaDoDia> paradas,
                             List<ItemRanking> ranking, long tempoConsultaMs) {
    }

    public record RecorteMes(String mes, String titulo, Indicadores indicadores, List<ItemSerie> dias,
                             List<ItemRanking> ranking, long tempoConsultaMs) {
    }

    public record RecortePeriodo(LocalDate inicio, LocalDate fim, String titulo, Indicadores indicadores,
                                 List<ItemSerie> meses, List<ItemRanking> ranking, List<ItemMotorista> motoristas,
                                 long tempoConsultaMs) {
    }

    private final RoteiroRepository roteiros;
    private final PontoRepository pontos;
    private final ParametroService parametros;

    public DashboardService(RoteiroRepository roteiros, PontoRepository pontos, ParametroService parametros) {
        this.roteiros = roteiros;
        this.pontos = pontos;
        this.parametros = parametros;
    }

    // ---------- Recorte: dia ----------

    @Transactional(readOnly = true)
    public RecorteDia dia(LocalDate data, Long gerenteId, Long motoristaId) {
        long inicio = System.nanoTime();
        BigDecimal jornada = parametros.obter().getJornadaHoras();
        List<Agregados.TotalPorData> totais = roteiros.totaisPorData(data, data, gerenteId, motoristaId);

        List<ParadaDoDia> paradas = new ArrayList<>();
        List<Roteiro> doDia = new ArrayList<>(roteiros.listarComPontosNaData(data, gerenteId, motoristaId));
        doDia.sort(Comparator.comparing((Roteiro r) -> r.getMotorista().getNome()));
        for (Roteiro roteiro : doDia) {
            for (Ponto ponto : roteiro.getPontos()) {
                if (ponto.isPartida() || ponto.getTempoParadoSeg() == null) {
                    continue;
                }
                paradas.add(new ParadaDoDia(roteiro.getId(), roteiro.getMotorista().getNome(), ponto.getOrdem(),
                        ponto.getEndereco(), ponto.getChegada().format(HORA), ponto.getSaida().format(HORA),
                        minutos(ponto.getTempoParadoSeg()), ponto.isAcimaDoMaximo()));
            }
        }
        String titulo = data.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", PT_BR));
        return new RecorteDia(data, titulo, indicadoresPorData(totais, jornada), paradas,
                ranking(data, data, gerenteId, motoristaId, 5), decorrido(inicio));
    }

    // ---------- Recorte: mês ----------

    @Transactional(readOnly = true)
    public RecorteMes mes(YearMonth mes, Long gerenteId, Long motoristaId) {
        long inicio = System.nanoTime();
        BigDecimal jornada = parametros.obter().getJornadaHoras();
        LocalDate primeiro = mes.atDay(1);
        LocalDate ultimo = mes.atEndOfMonth();
        List<Agregados.TotalPorData> totais = roteiros.totaisPorData(primeiro, ultimo, gerenteId, motoristaId);
        Map<LocalDate, Agregados.TotalPorData> porData = totais.stream()
                .collect(Collectors.toMap(Agregados.TotalPorData::data, Function.identity()));

        List<ItemSerie> dias = new ArrayList<>();
        for (LocalDate d = primeiro; !d.isAfter(ultimo); d = d.plusDays(1)) {
            Agregados.TotalPorData t = porData.get(d);
            long qtd = t == null ? 0 : nz(t.roteiros());
            long seg = t == null ? 0 : nz(t.segundosParados());
            dias.add(new ItemSerie(d.toString(), d.format(DIA_MES), qtd, minutos(seg),
                    CalculadoraTempoParado.percentualDaJornada(seg, qtd, jornada), t == null ? BigDecimal.ZERO : nz(t.custo())));
        }
        String titulo = nomeDoMes(mes);
        return new RecorteMes(mes.toString(), titulo, indicadoresPorData(totais, jornada), dias,
                ranking(primeiro, ultimo, gerenteId, motoristaId, 10), decorrido(inicio));
    }

    // ---------- Recorte: período ----------

    @Transactional(readOnly = true)
    public RecortePeriodo periodo(LocalDate inicioPeriodo, LocalDate fimPeriodo, Long gerenteId, Long motoristaId) {
        if (inicioPeriodo == null || fimPeriodo == null) {
            throw new RegraNegocioException("Informe o início e o fim do período.");
        }
        if (fimPeriodo.isBefore(inicioPeriodo)) {
            throw new RegraNegocioException("O fim do período não pode ser antes do início.");
        }
        if (inicioPeriodo.plusDays(MAXIMO_DIAS_PERIODO).isBefore(fimPeriodo)) {
            throw new RegraNegocioException("Escolha um período de até 12 meses.");
        }
        long inicio = System.nanoTime();
        BigDecimal jornada = parametros.obter().getJornadaHoras();
        List<Agregados.TotalPorMes> totais = roteiros.totaisPorMes(inicioPeriodo, fimPeriodo, gerenteId, motoristaId);
        Map<YearMonth, Agregados.TotalPorMes> porMes = totais.stream()
                .collect(Collectors.toMap(t -> YearMonth.of(t.ano(), t.mes()), Function.identity()));

        List<ItemSerie> meses = new ArrayList<>();
        for (YearMonth m = YearMonth.from(inicioPeriodo); !m.isAfter(YearMonth.from(fimPeriodo)); m = m.plusMonths(1)) {
            Agregados.TotalPorMes t = porMes.get(m);
            long qtd = t == null ? 0 : nz(t.roteiros());
            long seg = t == null ? 0 : nz(t.segundosParados());
            String rotulo = m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "") + "/" + (m.getYear() % 100);
            meses.add(new ItemSerie(m.toString(), rotulo, qtd, minutos(seg),
                    CalculadoraTempoParado.percentualDaJornada(seg, qtd, jornada), t == null ? BigDecimal.ZERO : nz(t.custo())));
        }

        List<ItemMotorista> motoristas = roteiros.totaisPorMotorista(inicioPeriodo, fimPeriodo, gerenteId, motoristaId)
                .stream()
                .map(t -> new ItemMotorista(t.motoristaId(), t.nome(), nz(t.roteiros()), minutos(nz(t.segundosParados())),
                        media(nz(t.segundosParados()), nz(t.roteiros())),
                        CalculadoraTempoParado.percentualDaJornada(nz(t.segundosParados()), nz(t.roteiros()), jornada),
                        nz(t.custo())))
                .toList();

        String titulo = inicioPeriodo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a "
                + fimPeriodo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return new RecortePeriodo(inicioPeriodo, fimPeriodo, titulo, indicadoresPorMes(totais, jornada), meses,
                ranking(inicioPeriodo, fimPeriodo, gerenteId, motoristaId, 10), motoristas, decorrido(inicio));
    }

    @Transactional(readOnly = true)
    public LocalDate ultimaDataComColeta(LocalDate ate, Long gerenteId) {
        return roteiros.ultimaDataComColeta(ate, gerenteId).orElse(ate);
    }

    // ---------- Cálculos comuns ----------

    private List<ItemRanking> ranking(LocalDate inicio, LocalDate fim, Long gerenteId, Long motoristaId, int limite) {
        List<Agregados.TempoPorEndereco> linhas = pontos.rankingPorEndereco(inicio, fim, gerenteId, motoristaId,
                PageRequest.of(0, limite));
        List<ItemRanking> ranking = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            Agregados.TempoPorEndereco l = linhas.get(i);
            ranking.add(new ItemRanking(i + 1, l.endereco(), nz(l.paradas()), minutos(nz(l.segundosParados())),
                    media(nz(l.segundosParados()), nz(l.paradas())), minutos(nz(l.maiorParadaSeg()))));
        }
        return ranking;
    }

    private Indicadores indicadoresPorData(List<Agregados.TotalPorData> linhas, BigDecimal jornada) {
        Soma soma = new Soma();
        for (Agregados.TotalPorData l : linhas) {
            soma.somar(l.roteiros(), l.segundosParados(), l.custo(), l.distanciaEstimada(), l.distanciaReal(),
                    l.distanciaEstimadaComReal(), l.kmPercorrido(), l.kmComConsumo(), l.litros());
        }
        return soma.indicadores(jornada);
    }

    private Indicadores indicadoresPorMes(List<Agregados.TotalPorMes> linhas, BigDecimal jornada) {
        Soma soma = new Soma();
        for (Agregados.TotalPorMes l : linhas) {
            soma.somar(l.roteiros(), l.segundosParados(), l.custo(), l.distanciaEstimada(), l.distanciaReal(),
                    l.distanciaEstimadaComReal(), l.kmPercorrido(), l.kmComConsumo(), l.litros());
        }
        return soma.indicadores(jornada);
    }

    /** Acumula as linhas agregadas (por data ou por mês) de um recorte. */
    private static final class Soma {
        long qtd;
        long seg;
        BigDecimal custo = BigDecimal.ZERO;
        BigDecimal estimada = BigDecimal.ZERO;
        BigDecimal real = BigDecimal.ZERO;
        BigDecimal estimadaComReal = BigDecimal.ZERO;
        BigDecimal km = BigDecimal.ZERO;
        BigDecimal kmComConsumo = BigDecimal.ZERO;
        BigDecimal litros = BigDecimal.ZERO;

        void somar(Long roteiros, Long segundos, BigDecimal custo, BigDecimal estimada, BigDecimal real,
                   BigDecimal estimadaComReal, BigDecimal km, BigDecimal kmComConsumo, BigDecimal litros) {
            this.qtd += nz(roteiros);
            this.seg += nz(segundos);
            this.custo = this.custo.add(nz(custo));
            this.estimada = this.estimada.add(nz(estimada));
            this.real = this.real.add(nz(real));
            this.estimadaComReal = this.estimadaComReal.add(nz(estimadaComReal));
            this.km = this.km.add(nz(km));
            this.kmComConsumo = this.kmComConsumo.add(nz(kmComConsumo));
            this.litros = this.litros.add(nz(litros));
        }

        /** A diferença compara só roteiros que têm as duas distâncias (real − estimada). */
        Indicadores indicadores(BigDecimal jornada) {
            BigDecimal custoPorKm = km.signum() > 0 ? custo.divide(km, 2, RoundingMode.HALF_UP) : null;
            BigDecimal kmLitroMedio = litros.signum() > 0 ? kmComConsumo.divide(litros, 1, RoundingMode.HALF_UP) : null;
            return new Indicadores(qtd, minutos(seg), media(seg, qtd),
                    CalculadoraTempoParado.percentualDaJornada(seg, qtd, jornada),
                    custo.setScale(2, RoundingMode.HALF_UP),
                    estimada.setScale(1, RoundingMode.HALF_UP),
                    real.setScale(1, RoundingMode.HALF_UP),
                    real.subtract(estimadaComReal).setScale(1, RoundingMode.HALF_UP),
                    jornada,
                    km.setScale(1, RoundingMode.HALF_UP),
                    custoPorKm,
                    litros.setScale(1, RoundingMode.HALF_UP),
                    kmLitroMedio);
        }
    }

    static String nomeDoMes(YearMonth mes) {
        String nome = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(nome.charAt(0)) + nome.substring(1) + " de " + mes.getYear();
    }

    private static long minutos(long segundos) {
        return Math.round(segundos / 60.0);
    }

    private static long media(long segundos, long quantidade) {
        return quantidade == 0 ? 0 : Math.round(segundos / 60.0 / quantidade);
    }

    private static long nz(Long valor) {
        return valor == null ? 0 : valor;
    }

    private static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static long decorrido(long inicioNano) {
        return (System.nanoTime() - inicioNano) / 1_000_000;
    }
}
