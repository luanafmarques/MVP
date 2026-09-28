package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import br.pucminas.pontomorto.servico.DashboardService;
import br.pucminas.pontomorto.servico.MotoristaService;
import br.pucminas.pontomorto.servico.ParametroService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** RF08: página do painel. Os números e gráficos vêm da API JSON ({@link PainelApiController}). */
@Controller
public class PainelController {

    private final DashboardService dashboard;
    private final MotoristaService motoristas;
    private final ParametroService parametros;
    private final RoteiroRepository roteiros;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public PainelController(DashboardService dashboard, MotoristaService motoristas, ParametroService parametros,
                            RoteiroRepository roteiros, ControleAcesso acesso, Clock relogio) {
        this.dashboard = dashboard;
        this.motoristas = motoristas;
        this.parametros = parametros;
        this.roteiros = roteiros;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    @GetMapping("/painel")
    public String painel(Model model) {
        UsuarioLogado usuario = acesso.exigirUsuario();
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate dia = dashboard.ultimaDataComColeta(hoje, usuario.escopoGerenteId());
        model.addAttribute("motoristas", motoristas.listar());
        model.addAttribute("diaPadrao", dia);
        model.addAttribute("mesPadrao", YearMonth.from(dia).toString());
        model.addAttribute("inicioPadrao", hoje.minusMonths(12).plusDays(1));
        model.addAttribute("fimPadrao", hoje);
        model.addAttribute("jornada", parametros.obter().getJornadaHoras());
        return "painel";
    }

    /** Estradas dos roteiros de um dia (fragmento HTML carregado no recorte "Dia"). */
    @GetMapping("/painel/estradas")
    public String estradas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                           @RequestParam(required = false) Long motoristaId, Model model) {
        UsuarioLogado usuario = acesso.exigirUsuario();
        List<Roteiro> doDia = new ArrayList<>(roteiros.listarComPontosNaData(data, usuario.escopoGerenteId(), motoristaId));
        doDia.sort(Comparator.comparing((Roteiro r) -> r.getMotorista().getNome()));
        long escala = doDia.stream().mapToLong(EstradaView::maiorParadaMin).max().orElse(0);
        model.addAttribute("estradas", doDia.stream().map(r -> EstradaView.de(r, escala)).toList());
        return "fragmentos/estrada :: lista";
    }
}
