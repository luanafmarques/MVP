package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.regras.CalculadoraCusto;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.servico.AuditoriaService;
import br.pucminas.pontomorto.servico.ParametroService;
import br.pucminas.pontomorto.web.Formularios.ParametroForm;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * RF09 e RF10: parâmetros de custo (combustível, km/l, custo por km), regras de tempo parado e jornada padrão,
 * alterados pela tela (critério de aceitação 4). Também a tela de auditoria (RNF05).
 */
@Controller
public class ParametroController {

    private final ParametroService parametros;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public ParametroController(ParametroService parametros, AuditoriaService auditoria, ControleAcesso acesso, Clock relogio) {
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    @GetMapping("/parametros")
    public String parametros(Model model) {
        Parametro atual = parametros.obter();
        return tela(ParametroForm.de(atual), atual, model);
    }

    @PostMapping("/parametros")
    public String salvar(@ModelAttribute("form") ParametroForm form, Model model, RedirectAttributes ra) {
        try {
            int recalculados = parametros.atualizar(form.alteracao(), form.isRecalcularConcluidos());
            Avisos.sucesso(ra, "Parâmetros salvos. " + recalculados + " roteiro(s) recalculado(s) com os novos valores.");
            return "redirect:/parametros";
        } catch (RegraNegocioException e) {
            model.addAttribute("avisoErro", e.getMessage());
            return tela(form, parametros.obter(), model);
        }
    }

    private String tela(ParametroForm form, Parametro atual, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("atual", atual);
        // Exemplo da fórmula do RF11 com os valores atuais: 30 km em um carro de 12 km/l.
        model.addAttribute("exemplo", CalculadoraCusto.composicao(BigDecimal.valueOf(30), BigDecimal.valueOf(12),
                atual.getPrecoCombustivel(), atual.getCustoPorKm()));
        return "parametros";
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                            @RequestParam(required = false) String entidade,
                            @RequestParam(defaultValue = "0") int pagina, Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate ini = inicio == null ? hoje.minusDays(30) : inicio;
        LocalDate f = fim == null ? hoje : fim;
        model.addAttribute("inicio", ini);
        model.addAttribute("fim", f);
        model.addAttribute("entidade", entidade);
        model.addAttribute("pagina", auditoria.pesquisar(ini, f, acesso.exigirUsuario().escopoGerenteId(), entidade, pagina));
        return "auditoria";
    }
}
