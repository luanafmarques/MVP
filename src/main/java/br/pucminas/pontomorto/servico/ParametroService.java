package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.ParametroRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Parâmetros de custo (RF09), de cálculo do tempo parado e da jornada (RF10).
 * Alterados pela tela, sem mudar código (critério de aceitação 4).
 */
@Service
public class ParametroService {

    /** Novos valores vindos da tela de parâmetros. */
    public record Alteracao(BigDecimal precoCombustivel, BigDecimal kmLitroPadrao, BigDecimal custoPorKm,
                            BigDecimal jornadaHoras, int ignorarParadaMenorQueMin, int tempoMaximoParadaMin) {
    }

    private final ParametroRepository repositorio;
    private final RoteiroRepository roteiros;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final CalculoRoteiroService calculo;
    private final Clock relogio;

    public ParametroService(ParametroRepository repositorio, RoteiroRepository roteiros, AuditoriaService auditoria,
                            ControleAcesso acesso, @Lazy CalculoRoteiroService calculo, Clock relogio) {
        this.repositorio = repositorio;
        this.roteiros = roteiros;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.calculo = calculo;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Parametro obter() {
        return repositorio.findById(Parametro.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("Parâmetros não encontrados. Verifique a migração V1."));
    }

    /**
     * Salva os novos parâmetros. Os roteiros ainda abertos são sempre recalculados; os já concluídos,
     * só se {@code recalcularConcluidos} for verdadeiro (assim o custo histórico não muda sem querer).
     *
     * @return quantidade de roteiros recalculados
     */
    @Transactional
    public int atualizar(Alteracao novo, boolean recalcularConcluidos) {
        validar(novo);
        Parametro p = obter();
        String entidade = "Parâmetro";
        auditoria.alteracao(entidade, p.getId(), "Parâmetros do sistema", "Preço do combustível (R$/litro)",
                p.getPrecoCombustivel(), novo.precoCombustivel(), null, null);
        auditoria.alteracao(entidade, p.getId(), "Parâmetros do sistema", "km por litro padrão",
                p.getKmLitroPadrao(), novo.kmLitroPadrao(), null, null);
        auditoria.alteracao(entidade, p.getId(), "Parâmetros do sistema", "Custo por km (R$)",
                p.getCustoPorKm(), novo.custoPorKm(), null, null);
        auditoria.alteracao(entidade, p.getId(), "Parâmetros do sistema", "Jornada padrão (horas)",
                p.getJornadaHoras(), novo.jornadaHoras(), null, null);
        auditoria.alteracao(entidade, p.getId(), "Parâmetros do sistema", "Ignorar paradas menores que (min)",
                p.getIgnorarParadaMenorQueMin(), novo.ignorarParadaMenorQueMin(), null, null);
        auditoria.alteracao(entidade, p.getId(), "Parâmetros do sistema", "Tempo máximo por parada (min)",
                p.getTempoMaximoParadaMin(), novo.tempoMaximoParadaMin(), null, null);

        p.setPrecoCombustivel(novo.precoCombustivel());
        p.setKmLitroPadrao(novo.kmLitroPadrao());
        p.setCustoPorKm(novo.custoPorKm());
        p.setJornadaHoras(novo.jornadaHoras());
        p.setIgnorarParadaMenorQueMin(novo.ignorarParadaMenorQueMin());
        p.setTempoMaximoParadaMin(novo.tempoMaximoParadaMin());
        p.registrarAtualizacao(acesso.loginAtual(), LocalDateTime.now(relogio));
        repositorio.save(p);

        List<Roteiro> afetados = recalcularConcluidos
                ? roteiros.findAll()
                : roteiros.findByStatusIn(List.of(StatusRoteiro.PLANEJADO, StatusRoteiro.EM_ANDAMENTO));
        afetados.forEach(r -> calculo.recalcular(r, p));
        return afetados.size();
    }

    private static void validar(Alteracao a) {
        if (a.precoCombustivel() == null || a.precoCombustivel().signum() < 0) {
            throw new RegraNegocioException("Informe o preço do combustível (zero ou mais).");
        }
        if (a.kmLitroPadrao() == null || a.kmLitroPadrao().signum() <= 0) {
            throw new RegraNegocioException("O km por litro padrão precisa ser maior que zero.");
        }
        if (a.custoPorKm() == null || a.custoPorKm().signum() < 0) {
            throw new RegraNegocioException("Informe o custo por km (zero ou mais).");
        }
        if (a.jornadaHoras() == null || a.jornadaHoras().signum() <= 0 || a.jornadaHoras().compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new RegraNegocioException("A jornada padrão precisa ficar entre 1 e 24 horas.");
        }
        if (a.ignorarParadaMenorQueMin() < 0 || a.tempoMaximoParadaMin() < 0) {
            throw new RegraNegocioException("Os limites de parada não podem ser negativos.");
        }
        if (a.tempoMaximoParadaMin() > 0 && a.ignorarParadaMenorQueMin() >= a.tempoMaximoParadaMin()) {
            throw new RegraNegocioException("O tempo mínimo para contar a parada precisa ser menor que o tempo máximo.");
        }
    }
}
