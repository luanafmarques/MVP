package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Auditoria;
import br.pucminas.pontomorto.dominio.Auditoria.Acao;
import br.pucminas.pontomorto.repositorio.AuditoriaRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * RNF05: auditoria de todas as alterações em pontos e horários (e nos demais cadastros importantes).
 * Cada campo alterado vira uma linha com quem, quando, valor antigo e valor novo.
 */
@Service
public class AuditoriaService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AuditoriaRepository repositorio;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public AuditoriaService(AuditoriaRepository repositorio, ControleAcesso acesso, Clock relogio) {
        this.repositorio = repositorio;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    /** Registra a alteração de um campo, só se o valor realmente mudou. */
    @Transactional
    public void alteracao(String entidade, Long entidadeId, String descricao, String campo,
                          Object antigo, Object novo, String motivo, Long gerenteId) {
        String textoAntigo = formatar(antigo);
        String textoNovo = formatar(novo);
        if (Objects.equals(textoAntigo, textoNovo)) {
            return;
        }
        salvar(Acao.ALTERACAO, entidade, entidadeId, descricao, campo, textoAntigo, textoNovo, motivo, gerenteId);
    }

    @Transactional
    public void criacao(String entidade, Long entidadeId, String descricao, Long gerenteId) {
        salvar(Acao.CRIACAO, entidade, entidadeId, descricao, null, null, null, null, gerenteId);
    }

    @Transactional
    public void exclusao(String entidade, Long entidadeId, String descricao, String motivo, Long gerenteId) {
        salvar(Acao.EXCLUSAO, entidade, entidadeId, descricao, null, null, null, motivo, gerenteId);
    }

    private void salvar(Acao acao, String entidade, Long entidadeId, String descricao, String campo,
                        String antigo, String novo, String motivo, Long gerenteId) {
        repositorio.save(new Auditoria(LocalDateTime.now(relogio), acesso.loginAtual(), acao, entidade, entidadeId,
                descricao, campo, antigo, novo, motivo, gerenteId));
    }

    @Transactional(readOnly = true)
    public Page<Auditoria> pesquisar(LocalDate inicio, LocalDate fim, Long gerenteId, String entidade, int pagina) {
        return repositorio.pesquisar(inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay(), gerenteId,
                entidade == null || entidade.isBlank() ? null : entidade, PageRequest.of(Math.max(pagina, 0), 50));
    }

    static String formatar(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof LocalDateTime dataHora) {
            return dataHora.format(DATA_HORA);
        }
        if (valor instanceof LocalDate data) {
            return data.format(DATA);
        }
        if (valor instanceof BigDecimal numero) {
            return numero.stripTrailingZeros().toPlainString();
        }
        String texto = valor.toString();
        return texto.isBlank() ? null : texto;
    }
}
