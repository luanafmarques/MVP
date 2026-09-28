package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Gerente;
import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.StatusPedido;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.GerenteRepository;
import br.pucminas.pontomorto.repositorio.PedidoRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Entrada de pedidos: o gerente cadastra o pedido; o sistema identifica o endereço (latitude e longitude)
 * e o pedido fica disponível para montar o roteiro do dia previsto.
 */
@Service
public class PedidoService {

    public record Dados(String numero, String cliente, String enderecoEntrega, BigDecimal latitude, BigDecimal longitude,
                        LocalDate dataPrevista, String observacao, Long gerenteId) {
    }

    private final PedidoRepository pedidos;
    private final GerenteRepository gerentes;
    private final GeocodificacaoService geocodificacao;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public PedidoService(PedidoRepository pedidos, GerenteRepository gerentes, GeocodificacaoService geocodificacao,
                         AuditoriaService auditoria, ControleAcesso acesso, Clock relogio) {
        this.pedidos = pedidos;
        this.gerentes = gerentes;
        this.geocodificacao = geocodificacao;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Pedido> pesquisar(LocalDate inicio, LocalDate fim, StatusPedido status) {
        return pedidos.pesquisar(inicio, fim, status, acesso.exigirUsuario().escopoGerenteId());
    }

    @Transactional(readOnly = true)
    public Pedido buscar(Long id) {
        Pedido pedido = pedidos.findById(id).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
        UsuarioLogado usuario = acesso.exigirUsuario();
        if (usuario.isGerente() && (pedido.getGerente() == null
                || !Objects.equals(pedido.getGerente().getId(), usuario.getGerenteId()))) {
            throw new AccessDeniedException("Esse pedido não é da sua equipe.");
        }
        return pedido;
    }

    @Transactional
    public Pedido criar(Dados dados) {
        acesso.exigirGestor();
        validar(dados);
        String numero = dados.numero().trim();
        if (pedidos.existsByNumeroIgnoreCase(numero)) {
            throw new RegraNegocioException("Já existe um pedido com o número " + numero + ".");
        }
        Pedido pedido = new Pedido(numero, dados.cliente().trim(), dados.enderecoEntrega().trim(), dados.dataPrevista(),
                LocalDateTime.now(relogio));
        pedido.setObservacao(MotoristaService.vazioParaNulo(dados.observacao()));
        pedido.setGerente(gerenteDestino(dados.gerenteId()));
        definirCoordenadas(pedido, dados);
        pedidos.save(pedido);
        auditoria.criacao("Pedido", pedido.getId(), "Pedido " + numero + " - " + pedido.getEnderecoEntrega(), gerenteId(pedido));
        return pedido;
    }

    @Transactional
    public Pedido atualizar(Long id, Dados dados) {
        acesso.exigirGestor();
        validar(dados);
        Pedido pedido = buscar(id);
        boolean mudouEndereco = !pedido.getEnderecoEntrega().equals(dados.enderecoEntrega().trim());
        if (pedido.getStatus() != StatusPedido.PENDENTE
                && (mudouEndereco || !pedido.getDataPrevista().equals(dados.dataPrevista()))) {
            throw new RegraNegocioException("Esse pedido já está em um roteiro. Tire o ponto do roteiro para mudar endereço ou data.");
        }
        String descricao = "Pedido " + pedido.getNumero();
        Long gerente = gerenteId(pedido);
        auditoria.alteracao("Pedido", id, descricao, "Cliente", pedido.getCliente(), dados.cliente().trim(), null, gerente);
        auditoria.alteracao("Pedido", id, descricao, "Endereço de entrega", pedido.getEnderecoEntrega(), dados.enderecoEntrega().trim(), null, gerente);
        auditoria.alteracao("Pedido", id, descricao, "Data prevista", pedido.getDataPrevista(), dados.dataPrevista(), null, gerente);
        auditoria.alteracao("Pedido", id, descricao, "Observação", pedido.getObservacao(), MotoristaService.vazioParaNulo(dados.observacao()), null, gerente);
        BigDecimal latAntes = pedido.getLatitude();
        BigDecimal lonAntes = pedido.getLongitude();

        pedido.setCliente(dados.cliente().trim());
        pedido.setEnderecoEntrega(dados.enderecoEntrega().trim());
        pedido.setDataPrevista(dados.dataPrevista());
        pedido.setObservacao(MotoristaService.vazioParaNulo(dados.observacao()));
        if (mudouEndereco && dados.latitude() == null) {
            pedido.setLatitude(null);
            pedido.setLongitude(null);
        }
        definirCoordenadas(pedido, dados);
        auditoria.alteracao("Pedido", id, descricao, "Latitude", latAntes, pedido.getLatitude(), null, gerente);
        auditoria.alteracao("Pedido", id, descricao, "Longitude", lonAntes, pedido.getLongitude(), null, gerente);
        return pedido;
    }

    @Transactional
    public void excluir(Long id) {
        acesso.exigirGestor();
        Pedido pedido = buscar(id);
        if (pedido.getStatus() != StatusPedido.PENDENTE) {
            throw new RegraNegocioException("Só é possível excluir pedidos que ainda não estão em um roteiro.");
        }
        pedidos.delete(pedido);
        auditoria.exclusao("Pedido", id, "Pedido " + pedido.getNumero() + " - " + pedido.getEnderecoEntrega(), null, gerenteId(pedido));
    }

    /** Usa as coordenadas digitadas (ajuste manual); se vierem vazias, busca pelo endereço (Nominatim). */
    private void definirCoordenadas(Pedido pedido, Dados dados) {
        if (dados.latitude() != null && dados.longitude() != null) {
            pedido.setLatitude(dados.latitude());
            pedido.setLongitude(dados.longitude());
        } else if (!pedido.temCoordenadas()) {
            geocodificacao.buscar(pedido.getEnderecoEntrega()).ifPresent(r -> {
                pedido.setLatitude(r.latitude());
                pedido.setLongitude(r.longitude());
            });
        }
    }

    private Gerente gerenteDestino(Long gerenteIdInformado) {
        UsuarioLogado usuario = acesso.exigirUsuario();
        Long id = usuario.isGerente() ? usuario.getGerenteId() : gerenteIdInformado;
        return id == null ? null : gerentes.findById(id).orElseThrow(() -> new NaoEncontradoException("Gerente não encontrado."));
    }

    private static void validar(Dados dados) {
        if (dados.numero() == null || dados.numero().isBlank()) {
            throw new RegraNegocioException("Informe o número do pedido.");
        }
        if (dados.cliente() == null || dados.cliente().isBlank()) {
            throw new RegraNegocioException("Informe o cliente.");
        }
        if (dados.enderecoEntrega() == null || dados.enderecoEntrega().isBlank()) {
            throw new RegraNegocioException("Informe o endereço de entrega.");
        }
        if (dados.dataPrevista() == null) {
            throw new RegraNegocioException("Informe a data prevista.");
        }
        LocalService.validarCoordenadas(dados.latitude(), dados.longitude());
    }

    private static Long gerenteId(Pedido pedido) {
        return pedido.getGerente() == null ? null : pedido.getGerente().getId();
    }
}
