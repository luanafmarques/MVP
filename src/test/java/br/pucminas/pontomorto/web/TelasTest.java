package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.DadosDeTeste;
import br.pucminas.pontomorto.TesteIntegracao;
import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.repositorio.LocalRepository;
import br.pucminas.pontomorto.repositorio.PedidoRepository;
import br.pucminas.pontomorto.servico.LgpdService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Abre todas as telas com o perfil certo (pega erros de template) e testa os fluxos principais pela web. */
@TesteIntegracao
@DisplayName("Telas e fluxos pela web")
class TelasTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DadosDeTeste dados;
    @Autowired
    private PedidoRepository pedidos;
    @Autowired
    private LocalRepository locais;
    @Autowired
    private LgpdService lgpd;

    @Test
    @DisplayName("páginas públicas")
    void publicas() throws Exception {
        for (String url : new String[]{"/", "/login", "/privacidade"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        mvc.perform(get("/")).andExpect(content().string(containsString("Todo minuto parado tem endereço")));
    }

    @Test
    @DisplayName("landing page: pedido de teste grátis")
    void testeGratis() throws Exception {
        mvc.perform(post("/teste-gratis").with(csrf()).param("nome", "Ana Lima").param("empresa", "Entregas BH")
                        .param("email", "ana@exemplo.com").param("consentimento", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithUserDetails(DadosDeTeste.ADMIN)
    @DisplayName("todas as telas do administrador abrem")
    void telasDoAdministrador() throws Exception {
        Long roteiroA = dados.roteiroA().getId();
        Long motorista = dados.motorista(DadosDeTeste.JOAO).getId();
        Long pedido = pedidos.findAll().get(0).getId();
        Long local = locais.findAll().get(0).getId();
        String[] urls = {
                "/painel", "/painel/estradas?data=" + dados.ontem(), "/historico", "/historico?somenteParadas=true",
                "/roteiros", "/roteiros/novo", "/roteiros/novo?data=" + dados.hoje().plusDays(1), "/roteiros/" + roteiroA,
                "/roteiros/" + dados.roteiroDe(DadosDeTeste.JOAO, dados.hoje()).getId(),
                "/pedidos", "/pedidos/novo", "/pedidos/" + pedido, "/motoristas", "/motoristas/novo", "/motoristas/" + motorista,
                "/locais", "/locais/novo", "/locais/" + local, "/gerentes", "/gerentes/novo", "/parametros", "/auditoria"};
        for (String url : urls) {
            MvcResult r = mvc.perform(get(url)).andReturn();
            assertThat(r.getResponse().getStatus()).as(url).isEqualTo(200);
        }
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("a estrada do roteiro mostra os pontos em ordem, com o tamanho pelo tempo parado")
    void estrada() throws Exception {
        String html = mvc.perform(get("/roteiros/" + dados.roteiroA().getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("marco-partida", "15 min", "10 min", "50 min");
        // 50 min é a maior parada: círculo maior que o de 10 min
        int d50 = diametro(html, "50 min");
        int d10 = diametro(html, "10 min");
        assertThat(d50).isGreaterThan(d10);
    }

    /** Diâmetro (--d) do marco cujo rótulo contém o texto. */
    private static int diametro(String html, String rotulo) {
        int fim = html.indexOf(">" + rotulo + "<");
        int inicio = html.lastIndexOf("--d:", fim);
        return Integer.parseInt(html.substring(inicio + 4, html.indexOf("px", inicio)));
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("RF12: exporta o histórico em CSV e PDF")
    void exportacao() throws Exception {
        String periodo = "?inicio=" + dados.ontem() + "&fim=" + dados.ontem();
        MvcResult csv = mvc.perform(get("/historico/exportar.csv" + periodo)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv"))).andReturn();
        String texto = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(texto).contains("Data;Motorista;Ordem;Endereço", "Rua Peru, 55", "Partida (não conta)");
        MvcResult pdf = mvc.perform(get("/historico/exportar.pdf" + periodo)).andExpect(status().isOk()).andReturn();
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("entrada de pedidos pela tela e montagem do roteiro com os pedidos do dia")
    void pedidoEMontagem() throws Exception {
        String dia = dados.hoje().plusDays(2).toString();
        mvc.perform(post("/pedidos").with(csrf()).param("numero", "PM-TESTE-1").param("cliente", "Cliente Teste")
                        .param("enderecoEntrega", "Rua Peru, 55 - Sion, Belo Horizonte/MG").param("dataPrevista", dia)
                        .param("latitude", "-19.9528").param("longitude", "-43.9335"))
                .andExpect(status().is3xxRedirection());
        Pedido novo = pedidos.findAll().stream().filter(p -> p.getNumero().equals("PM-TESTE-1")).findFirst().orElseThrow();
        assertThat(novo.getGerente()).isNotNull();

        mvc.perform(post("/roteiros").with(csrf()).param("motoristaId", dados.motorista(DadosDeTeste.MARIA).getId().toString())
                        .param("data", dia).param("localPartidaId", locais.findAll().get(0).getId().toString())
                        .param("pedidoIds", novo.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(novo.getPonto()).isNotNull();
        assertThat(novo.getPonto().getOrdem()).isEqualTo(2);
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("tela do motorista: botões Cheguei e Saí pela web")
    void telaDoMotorista() throws Exception {
        lgpd.registrarConsentimento(dados.motorista(DadosDeTeste.JOAO).getUsuario().getId());
        var roteiro = dados.roteiroDe(DadosDeTeste.JOAO, dados.hoje());
        mvc.perform(get("/motorista/hoje")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cheguei")))
                .andExpect(content().string(containsString("Saí")));
        mvc.perform(post("/motorista/pontos/{id}/saida", roteiro.getPontos().get(0).getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/motorista/pontos/{id}/chegada", roteiro.getPontos().get(1).getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(roteiro.getPontos().get(1).getChegada()).isNotNull();
        // O ponto atual fica destacado na estrada ("você está aqui")
        mvc.perform(get("/motorista/hoje")).andExpect(status().isOk()).andExpect(content().string(containsString("marco-atual")));
    }
}
