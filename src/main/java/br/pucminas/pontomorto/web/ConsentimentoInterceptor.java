package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import br.pucminas.pontomorto.servico.LgpdService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

/** RNF06: no primeiro acesso, o motorista precisa ler e aceitar o aviso de privacidade antes de usar o sistema. */
@Configuration
public class ConsentimentoInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final ControleAcesso acesso;
    private final LgpdService lgpd;

    public ConsentimentoInterceptor(ControleAcesso acesso, LgpdService lgpd) {
        this.acesso = acesso;
        this.lgpd = lgpd;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/motorista/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Optional<UsuarioLogado> usuario = acesso.usuarioAtual();
        if (usuario.isPresent() && usuario.get().isMotorista() && !lgpd.aceitouAvisoAtual(usuario.get().getId())) {
            response.sendRedirect(request.getContextPath() + "/privacidade/aceite");
            return false;
        }
        return true;
    }
}
