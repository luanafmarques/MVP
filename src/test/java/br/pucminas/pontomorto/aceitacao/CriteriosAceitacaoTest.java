package br.pucminas.pontomorto.aceitacao;

import br.pucminas.pontomorto.DadosDeTeste;
import br.pucminas.pontomorto.TesteIntegracao;
import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.repositorio.ParametroRepository;
import br.pucminas.pontomorto.repositorio.PontoRepository;
import br.pucminas.pontomorto.servico.CalculoRoteiroService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Os quatro critérios de aceitação do enunciado (seção 8). */
@TesteIntegracao
@DisplayName("Critérios de aceitação")
class CriteriosAceitacaoTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DadosDeTeste dados;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PontoRepository pontos;
    @Autowired
    private ParametroRepository parametros;
    @Autowired
    private CalculoRoteiroService calculo;
    @Autowired
    private EntityManager em;

    private JsonNode api(String url) throws Exception {
        String corpo = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    @Test
    @DisplayName("CA1: o sistema não computa tempo parado no ponto de partida")
    void ca1PartidaNaoConta() {
        Roteiro a = dados.roteiroA();
        Ponto partida = a.getPartida().orElseThrow();
        // A partida tem chegada e saída registradas (15 minutos na base)...
        assertThat(partida.getChegada()).isNotNull();
        assertThat(partida.getSaida()).isNotNull();
        assertThat(partida.getTempoBrutoMin()).isEqualTo(15);
        // ...mas não conta: tempo parado zero e o total do roteiro A é 15 + 10 + 50 = 75 minutos.
        assertThat(partida.getTempoParadoSeg()).isZero();
        assertThat(a.getTempoTotalParadoMin()).isEqualTo(75);
        assertThat(a.getPontos()).extracting(Ponto::getTempoParadoMin).containsExactly(0L, 15L, 10L, 50L);
        // Os roteiros B e C do enunciado também
        assertThat(dados.roteiroDe(DadosDeTeste.MARIA, dados.ontem()).getTempoTotalParadoMin()).isEqualTo(41);
        assertThat(dados.roteiroDe("pedro@pontomorto.com.br", dados.ontem()).getTempoTotalParadoMin()).isEqualTo(45);
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("CA1: o painel e o ranking também deixam a partida de fora")
    void ca1PainelSemPartida() throws Exception {
        JsonNode dia = api("/api/painel/dia?data=" + dados.ontem());
        assertThat(dia.path("indicadores").path("totalParadoMin").asLong()).isEqualTo(75 + 41 + 45);
        for (JsonNode parada : dia.path("paradas")) {
            assertThat(parada.path("ordem").asInt()).isGreaterThan(1);
            assertThat(parada.path("endereco").asText()).doesNotContain("Seg. Família");
        }
        for (JsonNode item : dia.path("ranking")) {
            assertThat(item.path("endereco").asText()).doesNotContain("Seg. Família");
        }
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("CA2: o painel mostra os três recortes - dia, mês e período")
    void ca2TresRecortes() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-aba=\"dia\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-aba=\"mes\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-aba=\"periodo\"")));

        JsonNode dia = api("/api/painel/dia?data=" + dados.ontem());
        assertThat(dia.path("paradas").size()).isEqualTo(9); // 3 roteiros × 3 paradas

        YearMonth mes = YearMonth.from(dados.ontem());
        JsonNode recorteMes = api("/api/painel/mes?mes=" + mes);
        assertThat(recorteMes.path("dias").size()).isEqualTo(mes.lengthOfMonth());

        LocalDate fim = dados.hoje();
        LocalDate inicio = fim.minusMonths(12).plusDays(1);
        JsonNode periodo = api("/api/painel/periodo?inicio=" + inicio + "&fim=" + fim);
        assertThat(periodo.path("meses").size()).isGreaterThanOrEqualTo(12);
        // Os dados de exemplo têm histórico em pelo menos 2 meses, então o gráfico mensal não fica vazio.
        long mesesComDados = 0;
        for (JsonNode m : periodo.path("meses")) {
            if (m.path("minutosParados").asLong() > 0) {
                mesesComDados++;
            }
        }
        assertThat(mesesComDados).isGreaterThanOrEqualTo(2);
        assertThat(periodo.path("ranking").size()).isPositive();
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("Objetivo do MVP: indicadores de custo por km percorrido e consumo km/litro no painel")
    void indicadoresDeCustoDoTrajeto() throws Exception {
        JsonNode ind = api("/api/painel/dia?data=" + dados.ontem()).path("indicadores");

        // Esperado a partir dos 3 roteiros de ontem: km real (hodômetro) e km/l usado em cada um
        BigDecimal km = BigDecimal.ZERO;
        BigDecimal litros = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        for (String motorista : List.of(DadosDeTeste.JOAO, DadosDeTeste.MARIA, "pedro@pontomorto.com.br")) {
            Roteiro r = dados.roteiroDe(motorista, dados.ontem());
            assertThat(r.getDistanciaRealKm()).isNotNull();
            km = km.add(r.getDistanciaParaCusto());
            litros = litros.add(r.getDistanciaParaCusto().divide(r.getKmLitroUsado(), 6, RoundingMode.HALF_UP));
            custo = custo.add(r.getCustoEstimado());
        }
        assertThat(ind.path("kmPercorrido").decimalValue()).isEqualByComparingTo(km.setScale(1, RoundingMode.HALF_UP));
        assertThat(ind.path("custoPorKm").decimalValue())
                .isEqualByComparingTo(custo.divide(km, 2, RoundingMode.HALF_UP));
        assertThat(ind.path("kmLitroMedio").decimalValue())
                .isEqualByComparingTo(km.divide(litros, 1, RoundingMode.HALF_UP));
        assertThat(ind.path("litros").decimalValue()).isEqualByComparingTo(litros.setScale(1, RoundingMode.HALF_UP));

        // Sem coleta no dia: os dois indicadores ficam vazios (a tela mostra "—")
        JsonNode vazio = api("/api/painel/dia?data=" + dados.hoje().plusDays(30)).path("indicadores");
        assertThat(vazio.path("custoPorKm").isNull()).isTrue();
        assertThat(vazio.path("kmLitroMedio").isNull()).isTrue();

        mvc.perform(get("/painel"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Custo por km rodado")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Consumo médio")));
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("CA3: todo tempo parado exibido está ligado a um endereço e a uma data/hora registrados")
    void ca3TempoLigadoAEnderecoEHorario() throws Exception {
        JsonNode dia = api("/api/painel/dia?data=" + dados.ontem());
        for (JsonNode parada : dia.path("paradas")) {
            assertThat(parada.path("endereco").asText()).isNotBlank();
            assertThat(parada.path("chegada").asText()).matches("\\d{2}:\\d{2}");
            assertThat(parada.path("saida").asText()).matches("\\d{2}:\\d{2}");
        }
        // No banco inteiro: nenhum tempo parado sem endereço, chegada e saída (fora a partida, que vale zero).
        long semRegistro = em.createQuery("""
                select count(p) from Ponto p
                 where p.tempoParadoSeg is not null and p.ordem > 1
                   and (p.chegada is null or p.saida is null or p.endereco is null or p.endereco = '')
                """, Long.class).getSingleResult();
        assertThat(semRegistro).isZero();
        assertThat(pontos.count()).isPositive();
        // O histórico mostra o endereço junto do tempo
        mvc.perform(get("/historico").param("inicio", dados.ontem().toString()).param("fim", dados.ontem().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rua Peru, 55")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("50 min")));
    }

    @Test
    @WithUserDetails(DadosDeTeste.ADMIN)
    @DisplayName("CA4: parâmetros de custo e de jornada são alterados pela tela, sem mudar código")
    void ca4ParametrosPelaTela() throws Exception {
        JsonNode antes = api("/api/painel/dia?data=" + dados.ontem());
        // 161 min em 3 jornadas de 8 h = 11,2%
        assertThat(antes.path("indicadores").path("percentualJornada").decimalValue()).isEqualByComparingTo("11.2");
        double custoAntes = antes.path("indicadores").path("custoTotal").asDouble();

        mvc.perform(post("/parametros").with(csrf())
                        .param("precoCombustivel", "7.50")
                        .param("kmLitroPadrao", "12")
                        .param("custoPorKm", "0.60")
                        .param("jornadaHoras", "6")
                        .param("ignorarParadaMenorQueMin", "1")
                        .param("tempoMaximoParadaMin", "240")
                        .param("recalcularConcluidos", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/parametros"));

        Parametro salvo = parametros.findById(Parametro.ID_UNICO).orElseThrow();
        assertThat(salvo.getJornadaHoras()).isEqualByComparingTo("6");
        assertThat(salvo.getPrecoCombustivel()).isEqualByComparingTo("7.50");
        assertThat(salvo.getAtualizadoPor()).isEqualTo(DadosDeTeste.ADMIN);

        JsonNode depois = api("/api/painel/dia?data=" + dados.ontem());
        // 161 min em 3 jornadas de 6 h = 14,9%
        assertThat(depois.path("indicadores").path("percentualJornada").decimalValue()).isEqualByComparingTo("14.9");
        assertThat(depois.path("indicadores").path("custoTotal").asDouble()).isGreaterThan(custoAntes);

        mvc.perform(get("/parametros"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"7.5")));
    }
}
