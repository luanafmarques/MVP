package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Local;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusPedido;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.LocalRepository;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.PedidoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Montagem e manutenção do roteiro diário (RF04 e entrada de pedidos).
 * <ul>
 *   <li>RN05: um roteiro por motorista por data.</li>
 *   <li>RN06: pontos em ordem sequencial; reordenar renumera tudo.</li>
 *   <li>Pedidos com o mesmo endereço viram um único ponto.</li>
 * </ul>
 */
@Service
public class RoteiroService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Valor alto usado na troca de ordem em duas etapas (ver {@link #persistirOrdem}). */
    private static final int DESLOCAMENTO_TEMPORARIO = 10_000;

    public record Montagem(Long motoristaId, LocalDate data, Long localPartidaId, List<Long> pedidoIds) {
    }

    private final RoteiroRepository roteiros;
    private final MotoristaRepository motoristas;
    private final PedidoRepository pedidos;
    private final LocalRepository locais;
    private final CalculoRoteiroService calculo;
    private final DistanciaService distancia;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final EntityManager em;
    private final Clock relogio;

    public RoteiroService(RoteiroRepository roteiros, MotoristaRepository motoristas, PedidoRepository pedidos,
                          LocalRepository locais, CalculoRoteiroService calculo, DistanciaService distancia,
                          AuditoriaService auditoria, ControleAcesso acesso, EntityManager em, Clock relogio) {
        this.roteiros = roteiros;
        this.motoristas = motoristas;
        this.pedidos = pedidos;
        this.locais = locais;
        this.calculo = calculo;
        this.distancia = distancia;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.em = em;
        this.relogio = relogio;
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public Roteiro buscar(Long id) {
        Roteiro roteiro = roteiros.buscarComPontos(id)
                .orElseThrow(() -> new NaoEncontradoException("Roteiro não encontrado."));
        acesso.verificar(roteiro);
        return roteiro;
    }

    @Transactional(readOnly = true)
    public List<Roteiro> listar(LocalDate inicio, LocalDate fim, Long motoristaId) {
        UsuarioLogado usuario = acesso.exigirUsuario();
        return roteiros.listar(inicio, fim, usuario.escopoGerenteId(), motoristaId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> pedidosPendentes(LocalDate data) {
        return pedidos.pendentesDoDia(data, acesso.exigirUsuario().escopoGerenteId());
    }

    // ---------- Montagem a partir dos pedidos ----------

    @Transactional
    public Roteiro montar(Montagem montagem) {
        acesso.exigirGestor();
        if (montagem.motoristaId() == null) {
            throw new RegraNegocioException("Escolha o motorista.");
        }
        if (montagem.data() == null) {
            throw new RegraNegocioException("Escolha a data do roteiro.");
        }
        Motorista motorista = motoristas.findById(montagem.motoristaId())
                .orElseThrow(() -> new NaoEncontradoException("Motorista não encontrado."));
        acesso.verificar(motorista);
        if (!motorista.isAtivo() || motorista.isAnonimizado()) {
            throw new RegraNegocioException("Esse motorista está inativo.");
        }
        verificarDuplicidade(motorista, montagem.data());

        List<Long> idsPedidos = montagem.pedidoIds() == null ? List.of() : montagem.pedidoIds();
        if (montagem.localPartidaId() == null && idsPedidos.isEmpty()) {
            throw new RegraNegocioException("Escolha o ponto de partida ou pelo menos um pedido.");
        }

        Roteiro roteiro = new Roteiro(montagem.data(), motorista, LocalDateTime.now(relogio));
        if (montagem.localPartidaId() != null) {
            roteiro.adicionarPonto(Ponto.deLocal(buscarLocal(montagem.localPartidaId())));
        }
        incluirPedidos(roteiro, carregarPedidos(idsPedidos, montagem.data()));

        distancia.estimarPara(roteiro);
        calculo.recalcular(roteiro);
        roteiros.save(roteiro);
        auditoria.criacao("Roteiro", roteiro.getId(),
                descricao(roteiro) + " com " + roteiro.getPontos().size() + " pontos", gerenteDe(roteiro));
        return roteiro;
    }

    /** RN05: bloqueia um segundo roteiro para o mesmo motorista na mesma data. */
    private void verificarDuplicidade(Motorista motorista, LocalDate data) {
        if (roteiros.existsByMotoristaIdAndData(motorista.getId(), data)) {
            throw new RegraNegocioException("O motorista " + motorista.getNome() + " já tem um roteiro em "
                    + data.format(DATA) + ". Cada motorista tem um único roteiro por dia: abra o roteiro existente "
                    + "para incluir pontos.");
        }
    }

    @Transactional
    public void adicionarPedidos(Long roteiroId, List<Long> pedidoIds) {
        Roteiro roteiro = buscarParaAlterar(roteiroId);
        if (pedidoIds == null || pedidoIds.isEmpty()) {
            throw new RegraNegocioException("Marque pelo menos um pedido.");
        }
        List<Pedido> novos = carregarPedidos(pedidoIds, roteiro.getData());
        incluirPedidos(roteiro, novos);
        atualizarDistanciaECusto(roteiro);
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao(roteiro), "Pedidos incluídos", null,
                novos.stream().map(Pedido::getNumero).collect(Collectors.joining(", ")), null, gerenteDe(roteiro));
    }

    @Transactional
    public void adicionarLocal(Long roteiroId, Long localId, boolean comoPartida) {
        Roteiro roteiro = buscarParaAlterar(roteiroId);
        Local local = buscarLocal(localId);
        Ponto ponto = roteiro.adicionarPonto(Ponto.deLocal(local));
        if (comoPartida) {
            if (roteiro.getPartida().map(Ponto::isConcluido).orElse(false)) {
                throw new RegraNegocioException("A partida atual já tem horário registrado; não dá para trocar.");
            }
            roteiro.moverPara(ponto, 1);
        }
        persistirOrdem(roteiro);
        atualizarDistanciaECusto(roteiro);
        auditoria.alteracao("Ponto", ponto.getId(), descricaoPonto(ponto), "Ponto incluído", null,
                ponto.getOrdem() + "º - " + ponto.getEndereco(), null, gerenteDe(roteiro));
    }

    /** Agrupa os pedidos por endereço: pedidos no mesmo endereço caem no mesmo ponto. */
    private void incluirPedidos(Roteiro roteiro, List<Pedido> lista) {
        Map<String, Ponto> porEndereco = new LinkedHashMap<>();
        for (Ponto existente : roteiro.getPontos()) {
            porEndereco.putIfAbsent(Pedido.chaveDeEndereco(existente.getEndereco()), existente);
        }
        for (Pedido pedido : lista) {
            String chave = Pedido.chaveDeEndereco(pedido.getEnderecoEntrega());
            Ponto ponto = porEndereco.get(chave);
            if (ponto == null) {
                ponto = roteiro.adicionarPonto(new Ponto(pedido.getEnderecoEntrega(), pedido.getLatitude(), pedido.getLongitude()));
                porEndereco.put(chave, ponto);
            } else if (!ponto.temCoordenadas() && pedido.temCoordenadas()) {
                ponto.setLatitude(pedido.getLatitude());
                ponto.setLongitude(pedido.getLongitude());
            }
            pedido.associarAoPonto(ponto);
        }
    }

    private List<Pedido> carregarPedidos(List<Long> ids, LocalDate data) {
        if (ids.isEmpty()) {
            return List.of();
        }
        UsuarioLogado usuario = acesso.exigirUsuario();
        Map<Long, Pedido> encontrados = pedidos.findAllById(ids).stream()
                .collect(Collectors.toMap(Pedido::getId, Function.identity()));
        List<Pedido> emOrdem = new ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            Pedido pedido = encontrados.get(id);
            if (pedido == null) {
                throw new NaoEncontradoException("Pedido não encontrado.");
            }
            if (usuario.isGerente() && (pedido.getGerente() == null
                    || !Objects.equals(pedido.getGerente().getId(), usuario.getGerenteId()))) {
                throw new RegraNegocioException("O pedido " + pedido.getNumero() + " não é da sua equipe.");
            }
            if (pedido.getStatus() != StatusPedido.PENDENTE) {
                throw new RegraNegocioException("O pedido " + pedido.getNumero() + " já está em um roteiro.");
            }
            if (!pedido.getDataPrevista().equals(data)) {
                throw new RegraNegocioException("O pedido " + pedido.getNumero() + " está previsto para "
                        + pedido.getDataPrevista().format(DATA) + ", não para " + data.format(DATA) + ".");
            }
            emOrdem.add(pedido);
        }
        return emOrdem;
    }

    // ---------- Reordenar e remover (RN06) ----------

    @Transactional
    public void mover(Long roteiroId, Long pontoId, int deslocamento) {
        Roteiro roteiro = buscarParaAlterar(roteiroId);
        Ponto ponto = roteiro.buscarPonto(pontoId);
        int ordemAntiga = ponto.getOrdem();
        roteiro.mover(ponto, deslocamento);
        if (ponto.getOrdem() == ordemAntiga) {
            return;
        }
        if (roteiro.isColetaIniciada() && (ordemAntiga == 1 || ponto.getOrdem() == 1)) {
            // A transação é desfeita, então a troca feita em memória não chega ao banco.
            throw new RegraNegocioException("A coleta já começou: a partida não pode mais ser trocada.");
        }
        persistirOrdem(roteiro);
        atualizarDistanciaECusto(roteiro);
        auditoria.alteracao("Ponto", ponto.getId(), descricaoPonto(ponto), "Ordem", ordemAntiga, ponto.getOrdem(),
                null, gerenteDe(roteiro));
    }

    @Transactional
    public void removerPonto(Long roteiroId, Long pontoId) {
        Roteiro roteiro = buscarParaAlterar(roteiroId);
        Ponto ponto = roteiro.buscarPonto(pontoId);
        if (ponto.getChegada() != null || ponto.getSaida() != null) {
            throw new RegraNegocioException("Esse ponto já tem horário registrado. Corrija os horários em vez de remover.");
        }
        String descricao = descricaoPonto(ponto);
        for (Pedido pedido : new ArrayList<>(ponto.getPedidos())) {
            pedido.liberarDoRoteiro();
        }
        ponto.getPedidos().clear();
        roteiro.removerPonto(ponto);
        persistirOrdem(roteiro);
        atualizarDistanciaECusto(roteiro);
        auditoria.exclusao("Ponto", pontoId, descricao, null, gerenteDe(roteiro));
    }

    /**
     * Grava a nova ordem em duas etapas: primeiro todos os pontos vão para uma faixa alta (10001, 10002...),
     * depois voltam para 1, 2, 3... Assim a restrição única (roteiro, ordem) do banco nunca é violada no meio da troca.
     */
    private void persistirOrdem(Roteiro roteiro) {
        List<Ponto> lista = roteiro.getPontos();
        for (int i = 0; i < lista.size(); i++) {
            lista.get(i).setOrdem(DESLOCAMENTO_TEMPORARIO + i + 1);
        }
        em.flush();
        roteiro.renumerar();
        em.flush();
    }

    // ---------- Distância, hodômetro e exclusão ----------

    @Transactional
    public void recalcularDistancia(Long roteiroId) {
        Roteiro roteiro = buscar(roteiroId);
        acesso.exigirGestor();
        BigDecimal antes = roteiro.getDistanciaEstimadaKm();
        atualizarDistanciaECusto(roteiro);
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao(roteiro), "Distância estimada (km)", antes,
                roteiro.getDistanciaEstimadaKm(), "Recalculada pelo gerente", gerenteDe(roteiro));
    }

    /** Correção do hodômetro pelo gerente (auditada). */
    @Transactional
    public void corrigirHodometro(Long roteiroId, BigDecimal kmInicial, BigDecimal kmFinal, String motivo) {
        acesso.exigirGestor();
        Roteiro roteiro = buscar(roteiroId);
        exigirMotivo(motivo);
        BigDecimal iniAntes = roteiro.getKmInicial();
        BigDecimal fimAntes = roteiro.getKmFinal();
        roteiro.definirHodometro(kmInicial, kmFinal);
        calculo.recalcular(roteiro);
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao(roteiro), "km inicial", iniAntes, kmInicial, motivo, gerenteDe(roteiro));
        auditoria.alteracao("Roteiro", roteiro.getId(), descricao(roteiro), "km final", fimAntes, kmFinal, motivo, gerenteDe(roteiro));
    }

    @Transactional
    public void excluir(Long roteiroId) {
        acesso.exigirGestor();
        Roteiro roteiro = buscar(roteiroId);
        if (roteiro.isColetaIniciada()) {
            throw new RegraNegocioException("Esse roteiro já tem horários registrados e faz parte do histórico; não pode ser excluído.");
        }
        pedidos.doRoteiro(roteiroId).forEach(Pedido::liberarDoRoteiro);
        String descricao = descricao(roteiro);
        Long gerente = gerenteDe(roteiro);
        roteiros.delete(roteiro);
        auditoria.exclusao("Roteiro", roteiroId, descricao, null, gerente);
    }

    // ---------- Apoio ----------

    private Roteiro buscarParaAlterar(Long roteiroId) {
        acesso.exigirGestor();
        Roteiro roteiro = buscar(roteiroId);
        if (roteiro.getStatus() == StatusRoteiro.CONCLUIDO) {
            throw new RegraNegocioException("Roteiro concluído não pode ser alterado.");
        }
        return roteiro;
    }

    private void atualizarDistanciaECusto(Roteiro roteiro) {
        distancia.estimarPara(roteiro);
        calculo.recalcular(roteiro);
    }

    private Local buscarLocal(Long id) {
        return locais.findById(id).orElseThrow(() -> new NaoEncontradoException("Ponto cadastrado não encontrado."));
    }

    static void exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da correção (fica registrado na auditoria).");
        }
    }

    static String descricao(Roteiro roteiro) {
        return "Roteiro de " + roteiro.getData().format(DATA) + " - " + roteiro.getMotorista().getNome();
    }

    static String descricaoPonto(Ponto ponto) {
        return "Ponto " + ponto.getOrdem() + " (" + ponto.getEndereco() + ") · " + descricao(ponto.getRoteiro());
    }

    static Long gerenteDe(Roteiro roteiro) {
        return roteiro.getMotorista().getGerente() == null ? null : roteiro.getMotorista().getGerente().getId();
    }
}
