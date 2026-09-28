package br.pucminas.pontomorto.config;

import br.pucminas.pontomorto.config.EnderecosExemplo.Endereco;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import br.pucminas.pontomorto.regras.CalculadoraDistancia;
import br.pucminas.pontomorto.regras.CalculadoraDistancia.Coordenada;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.servico.CalculoRoteiroService;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Gera roteiros concluídos de dias úteis para um período, com paradas sorteadas nos endereços de exemplo.
 * Usado nos dados de exemplo (gráficos de vários meses) e no teste de desempenho de 12 meses (RNF03).
 * A distância estimada é a linha reta (marcada como aproximada, sem consultar a API de rotas);
 * a distância real simula o hodômetro, um pouco maior que a linha reta.
 */
@Component
public class GeradorHistorico {

    private final RoteiroRepository roteiros;
    private final CalculoRoteiroService calculo;
    private final EntityManager em;

    public GeradorHistorico(RoteiroRepository roteiros, CalculoRoteiroService calculo, EntityManager em) {
        this.roteiros = roteiros;
        this.calculo = calculo;
        this.em = em;
    }

    /** Deve ser chamado dentro de uma transação. Devolve a quantidade de roteiros criados. */
    public int gerar(List<Motorista> motoristas, LocalDate inicio, LocalDate fim, Parametro parametro, Random sorteio) {
        int criados = 0;
        // O veículo (km/l) é lido no cálculo do custo; carrega antes, porque a sessão é limpa a cada lote.
        motoristas.forEach(m -> Hibernate.initialize(m.getVeiculo()));
        double[] hodometro = new double[motoristas.size()];
        for (int i = 0; i < hodometro.length; i++) {
            hodometro[i] = 20_000 + sorteio.nextInt(30_000);
        }
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            if (dia.getDayOfWeek() == DayOfWeek.SUNDAY || (dia.getDayOfWeek() == DayOfWeek.SATURDAY && sorteio.nextInt(3) > 0)) {
                continue;
            }
            for (int i = 0; i < motoristas.size(); i++) {
                if (sorteio.nextInt(100) < 8) {
                    continue; // folga ou falta
                }
                Roteiro roteiro = roteiroDoDia(motoristas.get(i), dia, sorteio);
                BigDecimal linhaReta = linhaReta(roteiro);
                roteiro.definirDistanciaEstimada(linhaReta, true);
                double real = linhaReta.doubleValue() * (1.25 + sorteio.nextDouble() * 0.3) + sorteio.nextDouble() * 3;
                BigDecimal kmInicial = BigDecimal.valueOf(hodometro[i]).setScale(1, RoundingMode.HALF_UP);
                hodometro[i] += Math.max(real, 1.0) + 0.5;
                BigDecimal kmFinal = BigDecimal.valueOf(hodometro[i] - 0.5).setScale(1, RoundingMode.HALF_UP);
                roteiro.definirHodometro(kmInicial, kmFinal);
                calculo.recalcular(roteiro, parametro);
                roteiros.save(roteiro);
                criados++;
                if (criados % 200 == 0) {
                    em.flush();
                    em.clear();
                }
            }
        }
        em.flush();
        em.clear();
        return criados;
    }

    private static Roteiro roteiroDoDia(Motorista motorista, LocalDate dia, Random sorteio) {
        Roteiro roteiro = new Roteiro(dia, motorista, dia.atTime(6, 30));
        roteiro.setStatus(StatusRoteiro.CONCLUIDO);

        LocalDateTime relogio = dia.atTime(7, 15).plusMinutes(sorteio.nextInt(75));
        Ponto partida = roteiro.adicionarPonto(new Ponto(EnderecosExemplo.BASE.texto(), EnderecosExemplo.BASE.lat(),
                EnderecosExemplo.BASE.lon()));
        partida.setChegada(relogio.minusMinutes(10 + sorteio.nextInt(20)));
        partida.setSaida(relogio);

        List<Endereco> sorteados = new ArrayList<>(EnderecosExemplo.TODOS);
        Collections.shuffle(sorteados, sorteio);
        int paradas = 3 + sorteio.nextInt(5);
        for (int i = 0; i < paradas; i++) {
            Endereco endereco = sorteados.get(i);
            relogio = relogio.plusMinutes(8 + sorteio.nextInt(30));
            Ponto ponto = roteiro.adicionarPonto(new Ponto(endereco.texto(), endereco.lat(), endereco.lon()));
            ponto.setChegada(relogio);
            relogio = relogio.plusMinutes(minutosParado(endereco, sorteio));
            ponto.setSaida(relogio);
        }
        return roteiro;
    }

    /** Tempo parado com cauda longa: a maioria das paradas é curta, algumas passam de uma hora. */
    private static long minutosParado(Endereco endereco, Random sorteio) {
        double base = Math.exp(2.0 + sorteio.nextGaussian() * 0.6);
        return Math.max(2, Math.min(200, Math.round(base * endereco.lentidao())));
    }

    private static BigDecimal linhaReta(Roteiro roteiro) {
        List<Coordenada> coordenadas = roteiro.getPontos().stream()
                .map(p -> new Coordenada(p.getLatitude().doubleValue(), p.getLongitude().doubleValue()))
                .toList();
        return CalculadoraDistancia.linhaRetaKm(coordenadas);
    }
}
