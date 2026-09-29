package br.pucminas.pontomorto.seguranca;

import br.pucminas.pontomorto.DadosDeTeste;
import br.pucminas.pontomorto.TesteIntegracao;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import br.pucminas.pontomorto.servico.LgpdService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RNF04: senha criptografada e acesso por perfil (motorista, gerente, administrador). */
@TesteIntegracao
@DisplayName("RNF04 - login e controle de acesso por perfil")
class ControleAcessoTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PasswordEncoder senhas;
    @Autowired
    private LgpdService lgpd;
    @Autowired
    private DadosDeTeste dados;

    @Test
    @DisplayName("as senhas são guardadas com BCrypt, nunca em texto puro")
    void senhaCriptografada() {
        String hash = usuarios.findByLoginIgnoreCase(DadosDeTeste.GERENTE).orElseThrow().getSenhaHash();
        assertThat(hash).startsWith("$2").doesNotContain("gerente123");
        assertThat(senhas.matches("gerente123", hash)).isTrue();
    }

    @Test
    @DisplayName("login certo leva o gerente ao painel e o motorista ao roteiro do dia; senha errada é recusada")
    void login() throws Exception {
        mvc.perform(formLogin("/login").user("login", DadosDeTeste.GERENTE).password("senha", "gerente123"))
                .andExpect(redirectedUrl("/painel"));
        mvc.perform(formLogin("/login").user("login", DadosDeTeste.JOAO).password("senha", "motorista123"))
                .andExpect(redirectedUrl("/motorista/hoje"));
        mvc.perform(formLogin("/login").user("login", DadosDeTeste.GERENTE).password("senha", "errada"))
                .andExpect(redirectedUrl("/login?erro"));
    }

    @Test
    @DisplayName("sem login, as telas internas pedem para entrar; a landing page é pública")
    void anonimo() throws Exception {
        mvc.perform(get("/painel")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/api/painel/dia").param("data", "2026-01-01")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("ponto morto")));
    }

    @Test
    @WithUserDetails(DadosDeTeste.JOAO)
    @DisplayName("motorista: só vê o próprio roteiro; não abre painel, histórico, cadastros nem parâmetros")
    void motorista() throws Exception {
        lgpd.registrarConsentimento(dados.motorista(DadosDeTeste.JOAO).getUsuario().getId());
        mvc.perform(get("/motorista/hoje")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Rua Peru, 55")));
        for (String url : new String[]{"/painel", "/historico", "/roteiros", "/pedidos", "/motoristas", "/parametros", "/auditoria"}) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/painel/dia").param("data", dados.ontem().toString())).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("gerente: pedidos, cadastros, roteiros, histórico, painel e auditoria; sem parâmetros e sem gerentes")
    void gerente() throws Exception {
        for (String url : new String[]{"/painel", "/historico", "/roteiros", "/pedidos", "/motoristas", "/locais", "/auditoria"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        mvc.perform(get("/parametros")).andExpect(status().isForbidden());
        mvc.perform(get("/gerentes")).andExpect(status().isForbidden());
        mvc.perform(get("/motorista/hoje")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(DadosDeTeste.ADMIN)
    @DisplayName("administrador: tudo, inclusive parâmetros e cadastro de gerentes")
    void administrador() throws Exception {
        for (String url : new String[]{"/painel", "/historico", "/roteiros", "/pedidos", "/motoristas", "/locais",
                "/auditoria", "/parametros", "/gerentes", "/gerentes/novo"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    @WithUserDetails(DadosDeTeste.GERENTE)
    @DisplayName("o gerente não vê motoristas de outra equipe")
    void equipe() throws Exception {
        mvc.perform(get("/motoristas")).andExpect(content().string(containsString("João Silva")))
                .andExpect(content().string(not(containsString("Profissional anonimizado"))));
    }
}
