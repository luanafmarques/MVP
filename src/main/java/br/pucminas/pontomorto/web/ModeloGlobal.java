package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import br.pucminas.pontomorto.servico.NaoEncontradoException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Dados comuns a todas as páginas e tratamento das mensagens de erro nas telas. */
@ControllerAdvice(basePackages = "br.pucminas.pontomorto.web")
public class ModeloGlobal {

    private final ControleAcesso acesso;

    public ModeloGlobal(ControleAcesso acesso) {
        this.acesso = acesso;
    }

    @ModelAttribute("usuario")
    public UsuarioLogado usuario() {
        return acesso.usuarioAtual().orElse(null);
    }

    /** Regra de negócio violada em uma tela de consulta: mostra a mensagem em uma página simples. */
    @ExceptionHandler(RegraNegocioException.class)
    public String regraNegocio(RegraNegocioException e, Model model, HttpServletResponse resposta) {
        resposta.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("titulo", "Não foi possível continuar");
        model.addAttribute("mensagem", e.getMessage());
        model.addAttribute("usuario", usuario());
        return "mensagem";
    }

    @ExceptionHandler(NaoEncontradoException.class)
    public String naoEncontrado(NaoEncontradoException e, Model model, HttpServletResponse resposta) {
        resposta.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("titulo", "Não encontrado");
        model.addAttribute("mensagem", e.getMessage());
        model.addAttribute("usuario", usuario());
        return "mensagem";
    }
}
