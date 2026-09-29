package br.pucminas.pontomorto.coleta;

import br.pucminas.pontomorto.DadosDeTeste;
import br.pucminas.pontomorto.TesteIntegracao;
import br.pucminas.pontomorto.dominio.Auditoria;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.AuditoriaRepository;
import br.pucminas.pontomorto.servico.ColetaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithUserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF05, RF06 e RNF05: coleta de chegada e saída pelo motorista, correção manual e auditoria. */
@TesteIntegracao
@DisplayName("Coleta dos pontos (RF05, RF06) e auditoria (RNF05)")
class ColetaServiceTest {

    @Autowired
    private ColetaService coleta;
    @Autowired
    private AuditoriaRepository auditorias;
    @Autowired
    private DadosDeTeste dados;

    private Roteiro roteiroDeHojeDoJoao() {
        return dados.roteiroDe(DadosDeTeste.JOAO, dados.hoje());
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("Cheguei e Saí gravam o horário atual e calculam o tempo parado; a partida não conta")
    void chegueiESai() {
        Roteiro r = roteiroDeHojeDoJoao();
        Ponto partida = r.getPontos().get(0);
        Ponto segundo = r.getPontos().get(1);

        coleta.registrarSaida(partida.getId());
        assertThat(r.getStatus()).isEqualTo(StatusRoteiro.EM_ANDAMENTO);
        assertThat(partida.getTempoParadoSeg()).isZero();

        coleta.registrarChegada(segundo.getId());
        coleta.registrarSaida(segundo.getId());
        assertThat(segundo.getChegada()).isNotNull();
        assertThat(segundo.getSaida()).isNotNull();
        assertThat(segundo.getTempoParadoSeg()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(r.getTempoTotalParadoSeg()).isEqualTo(segundo.getTempoParadoSeg());
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("Não dá para registrar a chegada antes de sair do ponto anterior")
    void sequenciaDosBotoes() {
        Roteiro r = roteiroDeHojeDoJoao();
        assertThatThrownBy(() -> coleta.registrarChegada(r.getPontos().get(2).getId()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("saída do ponto");
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("Correção manual: parada que passa da meia-noite é calculada com data e hora completas")
    void correcaoMeiaNoite() {
        Roteiro r = roteiroDeHojeDoJoao();
        Ponto p = r.getPontos().get(1);
        LocalDateTime chegada = dados.hoje().atTime(23, 40);
        coleta.corrigirHorarios(p.getId(), chegada, chegada.plusMinutes(45), "Teste de meia-noite");
        assertThat(p.getTempoParadoMin()).isEqualTo(45);
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("Correção com saída antes da chegada é recusada")
    void saidaAntesDaChegada() {
        Ponto p = roteiroDeHojeDoJoao().getPontos().get(1);
        LocalDateTime chegada = dados.hoje().atTime(10, 0);
        assertThatThrownBy(() -> coleta.corrigirHorarios(p.getId(), chegada, chegada.minusMinutes(5), "erro"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A saída não pode ser antes da chegada.");
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("RNF05: a correção fica na auditoria com quem, quando, valor antigo, valor novo e motivo")
    void auditoriaDaCorrecao() {
        Ponto p = roteiroDeHojeDoJoao().getPontos().get(1);
        LocalDateTime chegada = dados.hoje().atTime(9, 5);
        coleta.corrigirHorarios(p.getId(), chegada, chegada.plusMinutes(12), "Esqueci de tocar em Cheguei");

        List<Auditoria> registros = auditorias.findByEntidadeAndEntidadeIdOrderByDataHoraDesc("Ponto", p.getId());
        assertThat(registros).extracting(Auditoria::getCampo).contains("Chegada", "Saída");
        Auditoria chegadaAuditada = registros.stream().filter(a -> "Chegada".equals(a.getCampo())).findFirst().orElseThrow();
        assertThat(chegadaAuditada.getUsuario()).isEqualTo(DadosDeTeste.JOAO);
        assertThat(chegadaAuditada.getValorAntigo()).isNull();
        assertThat(chegadaAuditada.getValorNovo()).endsWith("09:05:00");
        assertThat(chegadaAuditada.getMotivo()).isEqualTo("Esqueci de tocar em Cheguei");
        assertThat(chegadaAuditada.getDataHora()).isNotNull();
    }

    @Test
    @WithUserDetails(DadosDeTeste.MARIA)
    @DisplayName("RNF04: um motorista não registra pontos do roteiro de outro motorista")
    void naoMexeNoRoteiroDosOutros() {
        Ponto doJoao = roteiroDeHojeDoJoao().getPontos().get(0);
        assertThatThrownBy(() -> coleta.registrarSaida(doJoao.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("Hodômetro: distância real = km final − km inicial; o km final precisa ser maior")
    void hodometro() {
        Roteiro r = roteiroDeHojeDoJoao();
        coleta.registrarKmInicial(r.getId(), new BigDecimal("1000.0"));
        assertThatThrownBy(() -> coleta.encerrar(r.getId(), null, new BigDecimal("990.0")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O km final precisa ser maior que o km inicial.");
        coleta.encerrar(r.getId(), null, new BigDecimal("1042.5"));
        assertThat(r.getDistanciaRealKm()).isEqualByComparingTo("42.5");
        assertThat(r.getStatus()).isEqualTo(StatusRoteiro.CONCLUIDO);
        assertThat(r.getDistanciaParaCusto()).isEqualByComparingTo("42.5");
    }
}
