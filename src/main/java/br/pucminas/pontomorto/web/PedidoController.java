package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.StatusPedido;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.servico.GerenteService;
import br.pucminas.pontomorto.servico.PedidoService;
import br.pucminas.pontomorto.web.Formularios.PedidoForm;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

/** Entrada de pedidos: número, cliente, endereço de entrega (com geocodificação), data prevista e observação. */
@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidos;
    private final GerenteService gerentes;
    private final Clock relogio;

    public PedidoController(PedidoService pedidos, GerenteService gerentes, Clock relogio) {
        this.pedidos = pedidos;
        this.gerentes = gerentes;
        this.relogio = relogio;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                        @RequestParam(required = false) StatusPedido status, Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate ini = inicio == null ? hoje.minusDays(1) : inicio;
        LocalDate f = fim == null ? hoje.plusDays(7) : fim;
        model.addAttribute("inicio", ini);
        model.addAttribute("fim", f);
        model.addAttribute("status", status);
        model.addAttribute("situacoes", StatusPedido.values());
        model.addAttribute("pedidos", pedidos.pesquisar(ini, f, status));
        return "pedidos/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        PedidoForm form = new PedidoForm();
        form.setDataPrevista(LocalDate.now(relogio).plusDays(1));
        return formulario(form, model);
    }

    @GetMapping("/{id}")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("situacao", pedidos.buscar(id).getStatus());
        return formulario(PedidoForm.de(pedidos.buscar(id)), model);
    }

    @PostMapping
    public String salvar(@ModelAttribute("form") PedidoForm form, Model model, RedirectAttributes ra) {
        try {
            if (form.getId() == null) {
                pedidos.criar(form.dados());
                Avisos.sucesso(ra, "Pedido " + form.getNumero() + " cadastrado.");
            } else {
                pedidos.atualizar(form.getId(), form.dados());
                Avisos.sucesso(ra, "Pedido " + form.getNumero() + " atualizado.");
            }
            return "redirect:/pedidos?inicio=" + form.getDataPrevista() + "&fim=" + form.getDataPrevista();
        } catch (RegraNegocioException e) {
            model.addAttribute("avisoErro", e.getMessage());
            return formulario(form, model);
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            pedidos.excluir(id);
            Avisos.sucesso(ra, "Pedido excluído.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/pedidos";
    }

    private String formulario(PedidoForm form, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("gerentes", gerentes.listar());
        return "pedidos/form";
    }
}
