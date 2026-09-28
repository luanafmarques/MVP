package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.regras.CalculadoraTempoParado;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.AuditoriaRepository;
import br.pucminas.pontomorto.servico.ColetaService;
import br.pucminas.pontomorto.servico.LocalService;
import br.pucminas.pontomorto.servico.MotoristaService;
import br.pucminas.pontomorto.servico.ParametroService;
import br.pucminas.pontomorto.servico.RoteiroService;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * RF04: montar o roteiro diário a partir dos pedidos, ligar pontos em ordem a um motorista e a uma data,
 * reordenar, e corrigir horários e hodômetro (gerente/administrador).
 */
@Controller
@RequestMapping("/roteiros")
public class RoteiroController {

    private final RoteiroService roteiros;
    private final ColetaService coleta;
    private final MotoristaService motoristas;
    private final LocalService locais;
    private final AuditoriaRepository auditorias;
    private final ParametroService parametros;
    private final Clock relogio;

    public RoteiroController(RoteiroService roteiros, ColetaService coleta, MotoristaService motoristas,
                             LocalService locais, AuditoriaRepository auditorias, ParametroService parametros,
                             Clock relogio) {
        this.roteiros = roteiros;
        this.coleta = coleta;
        this.motoristas = motoristas;
        this.locais = locais;
        this.auditorias = auditorias;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                        @RequestParam(required = false) Long motoristaId, Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate ini = inicio == null ? hoje.minusDays(7) : inicio;
        LocalDate f = fim == null ? hoje.plusDays(7) : fim;
        model.addAttribute("inicio", ini);
        model.addAttribute("fim", f);
        model.addAttribute("motoristaId", motoristaId);
        model.addAttribute("motoristas", motoristas.listar());
        model.addAttribute("roteiros", roteiros.listar(ini, f, motoristaId));
        model.addAttribute("hoje", hoje);
        return "roteiros/lista";
    }

    @GetMapping("/novo")
    public String novo(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                       @RequestParam(required = false) Long motoristaId, Model model) {
        LocalDate dia = data == null ? LocalDate.now(relogio).plusDays(1) : data;
        model.addAttribute("data", dia);
        model.addAttribute("motoristaId", motoristaId);
        model.addAttribute("motoristas", motoristas.listarAtivos());
        model.addAttribute("locais", locais.listarAtivos());
        model.addAttribute("pedidos", roteiros.pedidosPendentes(dia));
        return "roteiros/novo";
    }

    @PostMapping
    public String montar(@RequestParam(required = false) Long motoristaId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                         @RequestParam(required = false) Long localPartidaId,
                         @RequestParam(required = false) List<Long> pedidoIds,
                         RedirectAttributes ra) {
        try {
            Roteiro roteiro = roteiros.montar(new RoteiroService.Montagem(motoristaId, data, localPartidaId, pedidoIds));
            Avisos.sucesso(ra, "Roteiro montado com " + roteiro.getPontos().size() + " pontos. Confira a ordem do trajeto.");
            return "redirect:/roteiros/" + roteiro.getId();
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
            return "redirect:/roteiros/novo?data=" + data + (motoristaId == null ? "" : "&motoristaId=" + motoristaId);
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        Roteiro roteiro = roteiros.buscar(id);
        model.addAttribute("roteiro", roteiro);
        model.addAttribute("estrada", EstradaView.de(roteiro));
        model.addAttribute("pedidosPendentes", roteiros.pedidosPendentes(roteiro.getData()));
        model.addAttribute("locais", locais.listarAtivos());
        model.addAttribute("historico", auditorias.findByEntidadeAndEntidadeIdOrderByDataHoraDesc("Roteiro", id));
        BigDecimal jornada = parametros.obter().getJornadaHoras();
        model.addAttribute("jornada", jornada);
        model.addAttribute("percentual", CalculadoraTempoParado.percentualDaJornada(roteiro.getTempoTotalParadoSeg(), 1, jornada));
        return "roteiros/detalhe";
    }

    @PostMapping("/{id}/pontos/{pontoId}/subir")
    public String subir(@PathVariable Long id, @PathVariable Long pontoId, RedirectAttributes ra) {
        return executar(id, ra, null, r -> roteiros.mover(id, pontoId, -1));
    }

    @PostMapping("/{id}/pontos/{pontoId}/descer")
    public String descer(@PathVariable Long id, @PathVariable Long pontoId, RedirectAttributes ra) {
        return executar(id, ra, null, r -> roteiros.mover(id, pontoId, 1));
    }

    @PostMapping("/{id}/pontos/{pontoId}/remover")
    public String remover(@PathVariable Long id, @PathVariable Long pontoId, RedirectAttributes ra) {
        return executar(id, ra, "Ponto removido; os pedidos dele voltaram para a lista de pendentes.",
                r -> roteiros.removerPonto(id, pontoId));
    }

    @PostMapping("/{id}/pontos/{pontoId}/corrigir")
    public String corrigir(@PathVariable Long id, @PathVariable Long pontoId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime chegada,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime saida,
                           @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        return executar(id, ra, "Horários corrigidos e registrados na auditoria.",
                r -> coleta.corrigirHorarios(pontoId, chegada, saida, motivo));
    }

    @PostMapping("/{id}/pedidos")
    public String adicionarPedidos(@PathVariable Long id, @RequestParam(required = false) List<Long> pedidoIds,
                                   RedirectAttributes ra) {
        return executar(id, ra, "Pedidos incluídos no roteiro.", r -> roteiros.adicionarPedidos(id, pedidoIds));
    }

    @PostMapping("/{id}/locais")
    public String adicionarLocal(@PathVariable Long id, @RequestParam Long localId,
                                 @RequestParam(defaultValue = "false") boolean comoPartida, RedirectAttributes ra) {
        return executar(id, ra, "Ponto incluído no roteiro.", r -> roteiros.adicionarLocal(id, localId, comoPartida));
    }

    @PostMapping("/{id}/distancia")
    public String recalcularDistancia(@PathVariable Long id, RedirectAttributes ra) {
        return executar(id, ra, "Distância estimada recalculada.", r -> roteiros.recalcularDistancia(id));
    }

    @PostMapping("/{id}/hodometro")
    public String hodometro(@PathVariable Long id, @RequestParam(required = false) BigDecimal kmInicial,
                            @RequestParam(required = false) BigDecimal kmFinal,
                            @RequestParam(required = false) String motivo, RedirectAttributes ra) {
        return executar(id, ra, "Hodômetro corrigido e registrado na auditoria.",
                r -> roteiros.corrigirHodometro(id, kmInicial, kmFinal, motivo));
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            roteiros.excluir(id);
            Avisos.sucesso(ra, "Roteiro excluído. Os pedidos voltaram para a lista de pendentes.");
            return "redirect:/roteiros";
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
            return "redirect:/roteiros/" + id;
        }
    }

    private String executar(Long id, RedirectAttributes ra, String sucesso, Consumer<Long> acao) {
        try {
            acao.accept(id);
            if (sucesso != null) {
                Avisos.sucesso(ra, sucesso);
            }
        } catch (RegraNegocioException e) {
            Avisos.erro(ra, e.getMessage());
        }
        return "redirect:/roteiros/" + id;
    }
}
