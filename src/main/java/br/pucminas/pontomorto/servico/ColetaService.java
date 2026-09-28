package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusPedido;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import br.pucminas.pontomorto.regras.CalculadoraTempoParado;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.PontoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Coleta dos dados de cada ponto (RF05): botões "Cheguei" e "Saí" gravam o horário atual,
 * com correção manual auditada (RNF05). O tempo parado é recalculado a cada registro (RF06).
 */
@Service
public class ColetaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final RoteiroRepository roteiros;
    private final PontoRepository pontos;
    private final CalculoRoteiroService calculo;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public ColetaService(RoteiroRepository roteiros, PontoRepository pontos, CalculoRoteiroService calculo,
                         AuditoriaService auditoria, ControleAcesso acesso, Clock relogio) {
        this.roteiros = roteiros;
        this.pontos = pontos;
        this.calculo = calculo;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    /**
     * Roteiro que o motorista vê hoje. Se o roteiro de ontem ainda está em andamento (parada que passou
     * da meia-noite), ele continua aparecendo até ser encerrado.
     */
    @Transactional(readOnly = true)
    public Optional<Roteiro> roteiroAtual(UsuarioLogado motorista) {
        if (motorista.getMotoristaId() == null) {
            return Optional.empty();
        }
        LocalDate hoje = LocalDate.now(relogio);
        Optional<Roteiro> ontem = roteiros.buscarDoMotoristaNaData(motorista.getMotoristaId(), hoje.minusDays(1))
                .filter(r -> r.getStatus() == StatusRoteiro.EM_ANDAMENTO);
        return ontem.isPresent() ? ontem : roteiros.buscarDoMotoristaNaData(motorista.getMotoristaId(), hoje);
    }

    @Transactional
    public Ponto registrarChegada(Long pontoId) {
        Ponto ponto = pontoParaColeta(pontoId);
        Roteiro roteiro = ponto.getRoteiro();
        if (ponto.getChegada() != null) {
            throw new RegraNegocioException("A chegada neste ponto já foi registrada às " + ponto.getChegada().format(HORA)
                    + ". Para mudar, use \"Corrigir horário\".");
        }
        roteiro.getPontos().stream()
                .filter(p -> p.getOrdem() == ponto.getOrdem() - 1 && p.getSaida() == null)
                .findFirst()
                .ifPresent(anterior -> {
                    throw new RegraNegocioException("Registre primeiro a saída do ponto " + anterior.getOrdem()
                            + " (" + anterior.getEndereco() + ").");
                });
        LocalDateTime agora = agora();
        ponto.setChegada(agora);
        iniciar(roteiro);
        calculo.recalcular(roteiro);
        auditoria.alteracao("Ponto", ponto.getId(), RoteiroService.descricaoPonto(ponto), "Chegada", null, agora,
                "Botão \"Cheguei\"", RoteiroService.gerenteDe(roteiro));
        return ponto;
    }

    @Transactional
    public Ponto registrarSaida(Long pontoId) {
        Ponto ponto = pontoParaColeta(pontoId);
        Roteiro roteiro = ponto.getRoteiro();
        if (ponto.getSaida() != null) {
            throw new RegraNegocioException("A saída deste ponto já foi registrada às " + ponto.getSaida().format(HORA)
                    + ". Para mudar, use \"Corrigir horário\".");
        }
        if (ponto.getChegada() == null && !ponto.isPartida()) {
            throw new RegraNegocioException("Registre a chegada antes da saída.");
        }
        LocalDateTime agora = agora();
        CalculadoraTempoParado.validarHorarios(ponto.getChegada(), agora);
        ponto.setSaida(agora);
        iniciar(roteiro);
        calculo.recalcular(roteiro);
        auditoria.alteracao("Ponto", ponto.getId(), RoteiroService.descricaoPonto(ponto), "Saída", null, agora,
                "Botão \"Saí\"", RoteiroService.gerenteDe(roteiro));
        return ponto;
    }

    /** Correção manual dos horários (motorista no roteiro do dia; gerente e administrador em qualquer roteiro da equipe). */
    @Transactional
    public Ponto corrigirHorarios(Long pontoId, LocalDateTime chegada, LocalDateTime saida, String motivo) {
        RoteiroService.exigirMotivo(motivo);
        UsuarioLogado usuario = acesso.exigirUsuario();
        Ponto ponto = usuario.isMotorista() ? pontoParaColeta(pontoId) : buscarPonto(pontoId);
        Roteiro roteiro = ponto.getRoteiro();
        if (saida != null && chegada == null && !ponto.isPartida()) {
            throw new RegraNegocioException("Informe a chegada junto com a saída.");
        }
        CalculadoraTempoParado.validarHorarios(chegada, saida);

        LocalDateTime chegadaAntes = ponto.getChegada();
        LocalDateTime saidaAntes = ponto.getSaida();
        ponto.setChegada(truncar(chegada));
        ponto.setSaida(truncar(saida));
        if (chegada != null || saida != null) {
            iniciar(roteiro);
        }
        calculo.recalcular(roteiro);
        String descricao = RoteiroService.descricaoPonto(ponto);
        Long gerente = RoteiroService.gerenteDe(roteiro);
        auditoria.alteracao("Ponto", ponto.getId(), descricao, "Chegada", chegadaAntes, ponto.getChegada(), motivo, gerente);
        auditoria.alteracao("Ponto", ponto.getId(), descricao, "Saída", saidaAntes, ponto.getSaida(), motivo, gerente);
        return ponto;
    }

    /** km do hodômetro no início do roteiro. */
    @Transactional
    public void registrarKmInicial(Long roteiroId, BigDecimal kmInicial) {
        Roteiro roteiro = roteiroParaColeta(roteiroId);
        if (kmInicial == null) {
            throw new RegraNegocioException("Informe o km do hodômetro.");
        }
        BigDecimal antes = roteiro.getKmInicial();
        roteiro.definirHodometro(kmInicial, roteiro.getKmFinal());
        iniciar(roteiro);
        calculo.recalcular(roteiro);
        auditoria.alteracao("Roteiro", roteiro.getId(), RoteiroService.descricao(roteiro), "km inicial", antes, kmInicial,
                "Informado pelo motorista", RoteiroService.gerenteDe(roteiro));
    }

    /**
     * Encerra o roteiro com o km final do hodômetro. Distância real = km final − km inicial;
     * a partir daí, o custo usa a distância real.
     */
    @Transactional
    public void encerrar(Long roteiroId, BigDecimal kmInicialSeFaltar, BigDecimal kmFinal) {
        Roteiro roteiro = roteiroParaColeta(roteiroId);
        BigDecimal kmInicial = roteiro.getKmInicial() != null ? roteiro.getKmInicial() : kmInicialSeFaltar;
        if (kmFinal != null && kmInicial == null) {
            throw new RegraNegocioException("Informe também o km inicial do hodômetro.");
        }
        BigDecimal iniAntes = roteiro.getKmInicial();
        BigDecimal fimAntes = roteiro.getKmFinal();
        StatusRoteiro statusAntes = roteiro.getStatus();
        roteiro.definirHodometro(kmInicial, kmFinal);
        roteiro.setStatus(StatusRoteiro.CONCLUIDO);
        for (Ponto ponto : roteiro.getPontos()) {
            if (ponto.getSaida() != null) {
                ponto.getPedidos().forEach(p -> p.setStatus(StatusPedido.ENTREGUE));
            }
        }
        calculo.recalcular(roteiro);
        String descricao = RoteiroService.descricao(roteiro);
        Long gerente = RoteiroService.gerenteDe(roteiro);
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao, "km inicial", iniAntes, kmInicial, "Encerramento", gerente);
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao, "km final", fimAntes, kmFinal, "Encerramento", gerente);
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao, "Situação", statusAntes.getRotulo(),
                StatusRoteiro.CONCLUIDO.getRotulo(), "Roteiro encerrado", gerente);
    }

    // ---------- Apoio ----------

    private void iniciar(Roteiro roteiro) {
        if (roteiro.getStatus() == StatusRoteiro.PLANEJADO) {
            roteiro.setStatus(StatusRoteiro.EM_ANDAMENTO);
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS);
    }

    private static LocalDateTime truncar(LocalDateTime valor) {
        return valor == null ? null : valor.truncatedTo(ChronoUnit.SECONDS);
    }

    private Ponto buscarPonto(Long pontoId) {
        Ponto ponto = pontos.findById(pontoId).orElseThrow(() -> new NaoEncontradoException("Ponto não encontrado."));
        acesso.verificar(ponto.getRoteiro());
        return ponto;
    }

    /** O motorista só registra pontos do próprio roteiro do dia (RNF04), e o roteiro não pode estar concluído. */
    private Ponto pontoParaColeta(Long pontoId) {
        Ponto ponto = buscarPonto(pontoId);
        verificarRoteiroDoDia(ponto.getRoteiro());
        return ponto;
    }

    private Roteiro roteiroParaColeta(Long roteiroId) {
        Roteiro roteiro = roteiros.buscarComPontos(roteiroId)
                .orElseThrow(() -> new NaoEncontradoException("Roteiro não encontrado."));
        acesso.verificar(roteiro);
        verificarRoteiroDoDia(roteiro);
        return roteiro;
    }

    private void verificarRoteiroDoDia(Roteiro roteiro) {
        UsuarioLogado usuario = acesso.exigirUsuario();
        if (usuario.isMotorista()) {
            Long atual = roteiroAtual(usuario).map(Roteiro::getId).orElse(null);
            if (!Objects.equals(atual, roteiro.getId())) {
                throw new AccessDeniedException("Você só pode registrar pontos do seu roteiro do dia.");
            }
        }
        if (roteiro.getStatus() == StatusRoteiro.CONCLUIDO) {
            throw new RegraNegocioException("Este roteiro já foi encerrado.");
        }
    }

    /** Pedidos entregues em um ponto (para exibir na tela do motorista). */
    public static String numerosDosPedidos(Ponto ponto) {
        return ponto.getPedidos().stream().map(Pedido::getNumero).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
