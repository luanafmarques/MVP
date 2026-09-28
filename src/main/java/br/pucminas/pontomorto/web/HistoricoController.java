package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.servico.HistoricoService;
import br.pucminas.pontomorto.servico.MotoristaService;
import br.pucminas.pontomorto.servico.RelatorioService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** RF07 e RF12: histórico de pontos e tempos parados, com exportação em CSV e PDF. */
@Controller
public class HistoricoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HistoricoService historico;
    private final RelatorioService relatorios;
    private final MotoristaService motoristas;
    private final Clock relogio;

    public HistoricoController(HistoricoService historico, RelatorioService relatorios, MotoristaService motoristas,
                               Clock relogio) {
        this.historico = historico;
        this.relatorios = relatorios;
        this.motoristas = motoristas;
        this.relogio = relogio;
    }

    @GetMapping("/historico")
    public String historico(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                            @RequestParam(required = false) Long motoristaId,
                            @RequestParam(defaultValue = "false") boolean somenteParadas,
                            @RequestParam(defaultValue = "0") int pagina,
                            Model model) {
        HistoricoService.Filtro filtro = filtro(inicio, fim, motoristaId, somenteParadas);
        Page<Ponto> resultado = historico.pesquisar(filtro, pagina);
        model.addAttribute("filtro", filtro);
        model.addAttribute("pagina", resultado);
        model.addAttribute("motoristas", motoristas.listar());
        return "historico";
    }

    @GetMapping("/historico/exportar.csv")
    public ResponseEntity<byte[]> csv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                      @RequestParam(required = false) Long motoristaId,
                                      @RequestParam(defaultValue = "false") boolean somenteParadas) {
        List<Ponto> pontos = historico.todos(filtro(inicio, fim, motoristaId, somenteParadas));
        return arquivo(relatorios.csv(pontos), nomeArquivo(inicio, fim, "csv"), new MediaType("text", "csv", StandardCharsets.UTF_8));
    }

    @GetMapping("/historico/exportar.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                      @RequestParam(required = false) Long motoristaId,
                                      @RequestParam(defaultValue = "false") boolean somenteParadas) {
        List<Ponto> pontos = historico.todos(filtro(inicio, fim, motoristaId, somenteParadas));
        String motorista = motoristaId == null ? "todos" : motoristas.buscar(motoristaId).getNome();
        byte[] pdf = relatorios.pdf(pontos, inicio.format(DATA) + " a " + fim.format(DATA), motorista);
        return arquivo(pdf, nomeArquivo(inicio, fim, "pdf"), MediaType.APPLICATION_PDF);
    }

    private HistoricoService.Filtro filtro(LocalDate inicio, LocalDate fim, Long motoristaId, boolean somenteParadas) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate fimEfetivo = fim == null ? hoje : fim;
        LocalDate inicioEfetivo = inicio == null ? fimEfetivo.minusDays(6) : inicio;
        if (motoristaId != null) {
            Motorista m = motoristas.buscar(motoristaId); // confere o acesso à equipe
            motoristaId = m.getId();
        }
        return new HistoricoService.Filtro(inicioEfetivo, fimEfetivo, motoristaId, somenteParadas);
    }

    private static String nomeArquivo(LocalDate inicio, LocalDate fim, String extensao) {
        return "ponto-morto-historico-" + inicio + "-a-" + fim + "." + extensao;
    }

    private static ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome, MediaType tipo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(tipo)
                .body(conteudo);
    }
}
