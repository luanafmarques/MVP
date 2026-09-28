package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.PontoRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** RF07: histórico de pontos e tempos parados por período, com endereços, filtrável por data e motorista. */
@Service
public class HistoricoService {

    public record Filtro(LocalDate inicio, LocalDate fim, Long motoristaId, boolean somenteParadas) {
    }

    private static final int POR_PAGINA = 50;

    private final PontoRepository pontos;
    private final ControleAcesso acesso;

    public HistoricoService(PontoRepository pontos, ControleAcesso acesso) {
        this.pontos = pontos;
        this.acesso = acesso;
    }

    @Transactional(readOnly = true)
    public Page<Ponto> pesquisar(Filtro filtro, int pagina) {
        validar(filtro);
        return pontos.historico(filtro.inicio(), filtro.fim(), acesso.exigirUsuario().escopoGerenteId(),
                filtro.motoristaId(), filtro.somenteParadas(), PageRequest.of(Math.max(pagina, 0), POR_PAGINA));
    }

    @Transactional(readOnly = true)
    public List<Ponto> todos(Filtro filtro) {
        validar(filtro);
        return pontos.historicoCompleto(filtro.inicio(), filtro.fim(), acesso.exigirUsuario().escopoGerenteId(),
                filtro.motoristaId(), filtro.somenteParadas());
    }

    private static void validar(Filtro filtro) {
        if (filtro.inicio() == null || filtro.fim() == null) {
            throw new RegraNegocioException("Informe o início e o fim do período.");
        }
        if (filtro.fim().isBefore(filtro.inicio())) {
            throw new RegraNegocioException("O fim do período não pode ser antes do início.");
        }
    }
}
