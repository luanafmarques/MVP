package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.SolicitacaoTeste;
import br.pucminas.pontomorto.repositorio.SolicitacaoTesteRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import br.pucminas.pontomorto.servico.LgpdService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Páginas públicas: landing page (campanha), login, aviso de privacidade e acesso negado. */
@Controller
public class PublicoController {

    private final ControleAcesso acesso;
    private final SolicitacaoTesteRepository solicitacoes;
    private final Clock relogio;

    public PublicoController(ControleAcesso acesso, SolicitacaoTesteRepository solicitacoes, Clock relogio) {
        this.acesso = acesso;
        this.solicitacoes = solicitacoes;
        this.relogio = relogio;
    }

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @PostMapping("/teste-gratis")
    public String testeGratis(@RequestParam String nome, @RequestParam String empresa, @RequestParam String email,
                              @RequestParam(required = false) String telefone,
                              @RequestParam(required = false) Integer tamanhoFrota,
                              @RequestParam(defaultValue = "false") boolean consentimento,
                              RedirectAttributes ra) {
        if (nome.isBlank() || empresa.isBlank() || !email.contains("@")) {
            Avisos.erro(ra, "Preencha nome, empresa e um e-mail válido.");
            return "redirect:/#teste-gratis";
        }
        if (!consentimento) {
            Avisos.erro(ra, "Para entrarmos em contato, marque que você concorda com o uso dos seus dados.");
            return "redirect:/#teste-gratis";
        }
        solicitacoes.save(new SolicitacaoTeste(nome.trim(), empresa.trim(), email.trim(),
                telefone == null || telefone.isBlank() ? null : telefone.trim(), tamanhoFrota, true,
                LocalDateTime.now(relogio)));
        Avisos.sucesso(ra, "Pronto, " + nome.trim().split(" ")[0] + "! Em até 1 dia útil enviamos o acesso ao seu teste grátis de 30 dias.");
        return "redirect:/#teste-gratis";
    }

    @GetMapping("/login")
    public String login() {
        Optional<UsuarioLogado> usuario = acesso.usuarioAtual();
        if (usuario.isPresent()) {
            return usuario.get().isMotorista() ? "redirect:/motorista/hoje" : "redirect:/painel";
        }
        return "login";
    }

    @GetMapping("/privacidade")
    public String privacidade(Model model) {
        model.addAttribute("versaoAviso", LgpdService.VERSAO_AVISO);
        return "privacidade";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado(Model model) {
        model.addAttribute("titulo", "Acesso negado");
        model.addAttribute("mensagem", "Seu perfil não tem permissão para abrir esta página.");
        return "mensagem";
    }
}
