package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.TipoVeiculo;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.servico.GerenteService;
import br.pucminas.pontomorto.servico.LocalService;
import br.pucminas.pontomorto.servico.MotoristaService;
import br.pucminas.pontomorto.web.Formularios.GerenteForm;
import br.pucminas.pontomorto.web.Formularios.LocalForm;
import br.pucminas.pontomorto.web.Formularios.MotoristaForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Cadastros: motoristas/motoboys (RF01), gerentes com a equipe (RF02, só administrador)
 * e pontos cadastrados com coordenadas (RF03).
 */
@Controller
public class CadastroController {

    private final MotoristaService motoristas;
    private final GerenteService gerentes;
    private final LocalService locais;

    public CadastroController(MotoristaService motoristas, GerenteService gerentes, LocalService locais) {
        this.motoristas = motoristas;
        this.gerentes = gerentes;
        this.locais = locais;
    }

    // ---------- Motoristas (RF01) ----------

    @GetMapping("/motoristas")
    public String motoristas(Model model) {
        model.addAttribute("motoristas", motoristas.listar());
        return "motoristas/lista";
    }

    @GetMapping("/motoristas/novo")
    public String novoMotorista(Model model) {
        return formularioMotorista(new MotoristaForm(), model);
    }

    @GetMapping("/motoristas/{id}")
    public String editarMotorista(@PathVariable Long id, Model model) {
        model.addAttribute("motorista", motoristas.buscar(id));
        return formularioMotorista(MotoristaForm.de(motoristas.buscar(id)), model);
    }

    @PostMapping("/motoristas")
    public String salvarMotorista(@ModelAttribute("form") MotoristaForm form, Model model, RedirectAttributes ra) {
        try {
            if (form.getId() == null) {
                motoristas.criar(form.dados());
                Avisos.sucesso(ra, "Motorista " + form.getNome() + " cadastrado. No primeiro acesso, ele verá o aviso de privacidade.");
            } else {
                motoristas.atualizar(form.getId(), form.dados());
                Avisos.sucesso(ra, "Cadastro de " + form.getNome() + " atualizado.");
            }
            return "redirect:/motoristas";
        } catch (RegraNegocioException e) {
            model.addAttribute("avisoErro", e.getMessage());
            if (form.getId() != null) {
                model.addAttribute("motorista", motoristas.buscar(form.getId()));
            }
            return formularioMotorista(form, model);
        }
    }

    @PostMapping("/motoristas/{id}/ativo")
    public String ativoMotorista(@PathVariable Long id, @RequestParam boolean ativo, RedirectAttributes ra) {
        try {
            motoristas.alterarAtivo(id, ativo);
            Avisos.sucesso(ra, ativo ? "Cadastro reativado." : "Cadastro desativado: o motorista não consegue mais entrar.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motoristas";
    }

    @PostMapping("/motoristas/{id}/excluir")
    public String excluirMotorista(@PathVariable Long id, RedirectAttributes ra) {
        try {
            motoristas.excluir(id);
            Avisos.sucesso(ra, "Motorista excluído.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motoristas";
    }

    private String formularioMotorista(MotoristaForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("tipos", TipoVeiculo.values());
        model.addAttribute("gerentes", gerentes.listar());
        return "motoristas/form";
    }

    // ---------- Gerentes (RF02) - só administrador ----------

    @GetMapping("/gerentes")
    public String gerentes(Model model) {
        model.addAttribute("gerentes", gerentes.listar());
        return "gerentes/lista";
    }

    @GetMapping("/gerentes/novo")
    public String novoGerente(Model model) {
        return formularioGerente(new GerenteForm(), model);
    }

    @GetMapping("/gerentes/{id}")
    public String editarGerente(@PathVariable Long id, Model model) {
        return formularioGerente(GerenteForm.de(gerentes.buscar(id)), model);
    }

    @PostMapping("/gerentes")
    public String salvarGerente(@ModelAttribute("form") GerenteForm form, Model model, RedirectAttributes ra) {
        try {
            if (form.getId() == null) {
                gerentes.criar(form.dados());
                Avisos.sucesso(ra, "Gerente " + form.getNome() + " cadastrado.");
            } else {
                gerentes.atualizar(form.getId(), form.dados());
                Avisos.sucesso(ra, "Cadastro de " + form.getNome() + " atualizado.");
            }
            return "redirect:/gerentes";
        } catch (RegraNegocioException e) {
            model.addAttribute("avisoErro", e.getMessage());
            return formularioGerente(form, model);
        }
    }

    @PostMapping("/gerentes/{id}/excluir")
    public String excluirGerente(@PathVariable Long id, RedirectAttributes ra) {
        try {
            gerentes.excluir(id);
            Avisos.sucesso(ra, "Gerente excluído.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/gerentes";
    }

    private String formularioGerente(GerenteForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("motoristas", motoristas.listar());
        return "gerentes/form";
    }

    // ---------- Pontos cadastrados (RF03) ----------

    @GetMapping("/locais")
    public String locais(Model model) {
        model.addAttribute("locais", locais.listar());
        return "locais/lista";
    }

    @GetMapping("/locais/novo")
    public String novoLocal(Model model) {
        model.addAttribute("form", new LocalForm());
        return "locais/form";
    }

    @GetMapping("/locais/{id}")
    public String editarLocal(@PathVariable Long id, Model model) {
        model.addAttribute("form", LocalForm.de(locais.buscar(id)));
        return "locais/form";
    }

    @PostMapping("/locais")
    public String salvarLocal(@ModelAttribute("form") LocalForm form, Model model, RedirectAttributes ra) {
        try {
            if (form.getId() == null) {
                locais.criar(form.dados());
            } else {
                locais.atualizar(form.getId(), form.dados());
            }
            Avisos.sucesso(ra, "Ponto \"" + form.getNome() + "\" salvo.");
            return "redirect:/locais";
        } catch (RegraNegocioException e) {
            model.addAttribute("avisoErro", e.getMessage());
            return "locais/form";
        }
    }

    @PostMapping("/locais/{id}/ativo")
    public String ativoLocal(@PathVariable Long id, @RequestParam boolean ativo, RedirectAttributes ra) {
        locais.alterarAtivo(id, ativo);
        Avisos.sucesso(ra, ativo ? "Ponto reativado." : "Ponto desativado (o histórico não muda).");
        return "redirect:/locais";
    }
}
