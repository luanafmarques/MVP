package br.pucminas.pontomorto.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * RNF04: login com senha criptografada (BCrypt) e acesso por perfil.
 * <ul>
 *   <li>Público: landing page, login, aviso de privacidade e arquivos estáticos.</li>
 *   <li>Motorista: só a tela do próprio roteiro do dia.</li>
 *   <li>Gerente e administrador: pedidos, cadastros, roteiros, histórico, painel, relatórios e auditoria.</li>
 *   <li>Administrador: parâmetros, gerentes e LGPD (anonimizar/excluir dados).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/", "/login", "/teste-gratis", "/privacidade",
                                "/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.svg", "/error").permitAll()
                        .requestMatchers("/acesso-negado").authenticated()
                        .requestMatchers("/motorista/**", "/privacidade/aceite").hasRole("MOTORISTA")
                        .requestMatchers("/parametros/**", "/gerentes/**", "/lgpd/**").hasRole("ADMIN")
                        .anyRequest().hasAnyRole("ADMIN", "GERENTE"))
                .formLogin(login -> login
                        .loginPage("/login")
                        .usernameParameter("login")
                        .passwordParameter("senha")
                        .successHandler(redirecionarPorPerfil())
                        .failureUrl("/login?erro")
                        .permitAll())
                .logout(sair -> sair
                        .logoutUrl("/sair")
                        .logoutSuccessUrl("/login?saiu")
                        .permitAll())
                .exceptionHandling(erros -> erros.accessDeniedPage("/acesso-negado"));
        return http.build();
    }

    /** Depois do login, o motorista vai direto para o roteiro do dia; os demais, para o painel. */
    private AuthenticationSuccessHandler redirecionarPorPerfil() {
        return (request, response, authentication) -> {
            boolean motorista = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_MOTORISTA"));
            response.sendRedirect(request.getContextPath() + (motorista ? "/motorista/hoje" : "/painel"));
        };
    }
}
