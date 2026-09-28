package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.servico.ColetaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * RF05: tela do motorista (pensada para o celular). Botões grandes "Cheguei" e "Saí" gravam o horário atual;
 * "Corrigir horário" permite ajuste manual com motivo (auditado). O motorista só vê o próprio roteiro do dia (RNF04).
 */
@Controller
@RequestMapping("/motorista")
public class MotoristaAppController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ColetaService coleta;
    private final ControleAcesso acesso;

    public MotoristaAppController(ColetaService coleta, ControleAcesso acesso) {
        this.coleta = coleta;
        this.acesso = acesso;
    }

    @GetMapping("/hoje")
    public String hoje(Model model) {
        Optional<Roteiro> roteiro = coleta.roteiroAtual(acesso.exigirUsuario());
        roteiro.ifPresent(r -> {
            model.addAttribute("roteiro", r);
            model.addAttribute("estrada", EstradaView.de(r));
            model.addAttribute("atual", r.getPontoAtual().orElse(null));
        });
        return "motorista/hoje";
    }

    @PostMapping("/pontos/{id}/chegada")
    public String chegada(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Ponto p = coleta.registrarChegada(id);
            Avisos.sucesso(ra, "Chegada registrada às " + p.getChegada().format(HORA) + ".");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motorista/hoje";
    }

    @PostMapping("/pontos/{id}/saida")
    public String saida(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Ponto p = coleta.registrarSaida(id);
            Avisos.sucesso(ra, p.isPartida()
                    ? "Saída da partida registrada às " + p.getSaida().format(HORA) + ". Boa viagem!"
                    : "Saída registrada às " + p.getSaida().format(HORA) + ". Tempo parado: " + p.getTempoParadoMin() + " min.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motorista/hoje";
    }

    @PostMapping("/pontos/{id}/corrigir")
    public String corrigir(@PathVariable Long id,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime chegada,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime saida,
                           @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        try {
            coleta.corrigirHorarios(id, chegada, saida, motivo);
            Avisos.sucesso(ra, "Horário corrigido. A correção fica registrada para o seu gerente.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motorista/hoje";
    }

    @PostMapping("/roteiros/{id}/km-inicial")
    public String kmInicial(@PathVariable Long id, @RequestParam(required = false) BigDecimal kmInicial,
                            RedirectAttributes ra) {
        try {
            coleta.registrarKmInicial(id, kmInicial);
            Avisos.sucesso(ra, "km inicial registrado.");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motorista/hoje";
    }

    @PostMapping("/roteiros/{id}/encerrar")
    public String encerrar(@PathVariable Long id, @RequestParam(required = false) BigDecimal kmInicial,
                           @RequestParam(required = false) BigDecimal kmFinal, RedirectAttributes ra) {
        try {
            coleta.encerrar(id, kmInicial, kmFinal);
            Avisos.sucesso(ra, "Roteiro encerrado. Bom descanso!");
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/motorista/hoje";
    }
}
