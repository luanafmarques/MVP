package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.servico.DashboardService;
import br.pucminas.pontomorto.servico.GeocodificacaoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API JSON usada pelas telas: os três recortes do painel (dia, mês e período) e a geocodificação de endereços.
 * O gerente vê só a própria equipe; o administrador vê tudo.
 */
@RestController
@RequestMapping("/api")
public class PainelApiController {

    private final DashboardService dashboard;
    private final GeocodificacaoService geocodificacao;
    private final ControleAcesso acesso;

    public PainelApiController(DashboardService dashboard, GeocodificacaoService geocodificacao, ControleAcesso acesso) {
        this.dashboard = dashboard;
        this.geocodificacao = geocodificacao;
        this.acesso = acesso;
    }

    @GetMapping("/painel/dia")
    public DashboardService.RecorteDia dia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                           @RequestParam(required = false) Long motoristaId) {
        return dashboard.dia(data, acesso.exigirUsuario().escopoGerenteId(), motoristaId);
    }

    @GetMapping("/painel/mes")
    public DashboardService.RecorteMes mes(@RequestParam String mes, @RequestParam(required = false) Long motoristaId) {
        YearMonth anoMes;
        try {
            anoMes = YearMonth.parse(mes);
        } catch (RuntimeException e) {
            throw new RegraNegocioException("Mês inválido. Use o formato AAAA-MM.");
        }
        return dashboard.mes(anoMes, acesso.exigirUsuario().escopoGerenteId(), motoristaId);
    }

    @GetMapping("/painel/periodo")
    public DashboardService.RecortePeriodo periodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Long motoristaId) {
        return dashboard.periodo(inicio, fim, acesso.exigirUsuario().escopoGerenteId(), motoristaId);
    }

    /** RF03: busca latitude e longitude do endereço (Nominatim/OpenStreetMap). */
    @GetMapping("/geocodificar")
    public Map<String, Object> geocodificar(@RequestParam String endereco) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        if (!geocodificacao.isHabilitado()) {
            resposta.put("encontrado", false);
            resposta.put("mensagem", "A busca automática de coordenadas está desligada. Digite latitude e longitude.");
            return resposta;
        }
        geocodificacao.buscar(endereco).ifPresentOrElse(r -> {
            resposta.put("encontrado", true);
            resposta.put("latitude", r.latitude());
            resposta.put("longitude", r.longitude());
            resposta.put("enderecoEncontrado", r.enderecoEncontrado());
        }, () -> {
            resposta.put("encontrado", false);
            resposta.put("mensagem", "Endereço não encontrado. Confira o texto (rua, número, cidade) ou ajuste no mapa.");
        });
        return resposta;
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, String>> erro(RegraNegocioException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }
}
