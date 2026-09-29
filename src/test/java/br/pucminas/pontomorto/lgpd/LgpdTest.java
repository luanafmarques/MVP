package br.pucminas.pontomorto.lgpd;

import br.pucminas.pontomorto.DadosDeTeste;
import br.pucminas.pontomorto.TesteIntegracao;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.repositorio.ConsentimentoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.servico.LgpdService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RNF06 (LGPD): aviso e consentimento, documento mascarado e anonimização/exclusão pelo administrador. */
@TesteIntegracao
@DisplayName("RNF06 - LGPD")
class LgpdTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DadosDeTeste dados;
    @Autowired
    private LgpdService lgpd;
    @Autowired
    private ConsentimentoRepository consentimentos;
    @Autowired
    private RoteiroRepository roteiros;

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("no primeiro acesso, o motorista vê o aviso de privacidade e o aceite fica registrado")
    void consentimentoNoPrimeiroAcesso() throws Exception {
        Long usuarioId = dados.motorista(DadosDeTeste.JOAO).getUsuario().getId();
        assertThat(lgpd.aceitouAvisoAtual(usuarioId)).isFalse();

        mvc.perform(get("/motorista/hoje")).andExpect(redirectedUrl("/privacidade/aceite"));
        mvc.perform(get("/privacidade/aceite")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aviso de privacidade")));
        // Sem marcar "concordo", não passa
        mvc.perform(post("/privacidade/aceite").with(csrf())).andExpect(redirectedUrl("/privacidade/aceite"));
        mvc.perform(post("/privacidade/aceite").with(csrf()).param("concordo", "true")).andExpect(redirectedUrl("/motorista/hoje"));

        assertThat(lgpd.aceitouAvisoAtual(usuarioId)).isTrue();
        assertThat(consentimentos.findFirstByUsuarioIdOrderByAceitoEmDesc(usuarioId)).get()
                .satisfies(c -> assertThat(c.getVersaoAviso()).isEqualTo(LgpdService.VERSAO_AVISO));
        mvc.perform(get("/motorista/hoje")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("o documento aparece mascarado nas listas")
    void documentoMascarado() throws Exception {
        assertThat(Motorista.mascarar("12345678909")).isEqualTo("***.456.***-**");
        mvc.perform(get("/motoristas"))
                .andExpect(content().string(containsString("***.456.***-**")))
                .andExpect(content().string(not(containsString("12345678909"))));
    }

    @Test
    @WithUserDetails(DadosDeTeste.ADMIN)
    @DisplayName("o administrador anonimiza os dados pessoais; o histórico de tempos continua, sem identificar a pessoa")
    void anonimizar() throws Exception {
        Motorista joao = dados.motorista(DadosDeTeste.JOAO);
        Long id = joao.getId();
        mvc.perform(post("/lgpd/motoristas/{id}/anonimizar", id).with(csrf())).andExpect(redirectedUrl("/motoristas"));

        assertThat(joao.isAnonimizado()).isTrue();
        assertThat(joao.getNome()).isEqualTo("Profissional anonimizado #" + id);
        assertThat(joao.getDocumento()).isNull();
        assertThat(joao.getTelefone()).isNull();
        assertThat(joao.getUsuario().isAtivo()).isFalse();
        assertThat(joao.getUsuario().getLogin()).doesNotContain("joao");
        assertThat(roteiros.buscarDoMotoristaNaData(id, dados.ontem())).get()
                .satisfies(r -> assertThat(r.getTempoTotalParadoMin()).isEqualTo(75));
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("só o administrador pode anonimizar ou excluir dados pessoais")
    void gerenteNaoAnonimiza() throws Exception {
        Long id = dados.motorista(DadosDeTeste.JOAO).getId();
        mvc.perform(post("/lgpd/motoristas/{id}/anonimizar", id).with(csrf())).andExpect(status().isForbidden());
    }
}
