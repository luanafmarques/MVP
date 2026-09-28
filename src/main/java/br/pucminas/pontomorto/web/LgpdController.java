package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.servico.LgpdService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * RNF06 (LGPD): aceite do aviso de privacidade no primeiro acesso do motorista e, para o administrador,
 * anonimização ou exclusão dos dados pessoais de um profissional.
 */
@Controller
public class LgpdController {

    private final LgpdService lgpd;
    private final ControleAcesso acesso;

    public LgpdController(LgpdService lgpd, ControleAcesso acesso) {
        this.lgpd = lgpd;
        this.acesso = acesso;
    }

    @GetMapping("/privacidade/aceite")
    public String aviso(Model model) {
        if (lgpd.aceitouAvisoAtual(acesso.exigirUsuario().getId())) {
            return "redirect:/motorista/hoje";
        }
        model.addAttribute("versaoAviso", LgpdService.VERSAO_AVISO);
        return "privacidade-aceite";
    }

    @PostMapping("/privacidade/aceite")
    public String aceitar(@RequestParam(defaultValue = "false") boolean concordo, RedirectAttributes ra) {
        if (!concordo) {
            Avisos.erro(ra, "Para usar o sistema, marque que você leu e concorda com o aviso de privacidade.");
            return "redirect:/privacidade/aceite";
        }
        lgpd.registrarConsentimento(acesso.exigirUsuario().getId());
        Avisos.sucesso(ra, "Obrigado! Seu aceite foi registrado.");
        return "redirect:/motorista/hoje";
    }

    @PostMapping("/lgpd/motoristas/{id}/anonimizar")
    public String anonimizar(@PathVariable Long id, RedirectAttributes ra) {
        try {
            lgpd.anonimizar(id);
            Avisos.sucesso(ra, "Dados pessoais anonimizados. O histórico de tempos continua disponível, sem identificar a pessoa.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motoristas";
    }

    @PostMapping("/lgpd/motoristas/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            boolean excluido = lgpd.excluirDadosPessoais(id);
            Avisos.sucesso(ra, excluido
                    ? "Cadastro e dados pessoais excluídos."
                    : "O profissional tem roteiros no histórico, então os dados pessoais foram anonimizados (o histórico continua, sem identificação).");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motoristas";
    }
}
