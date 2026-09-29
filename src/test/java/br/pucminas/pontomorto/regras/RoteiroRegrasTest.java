package br.pucminas.pontomorto.regras;

import br.pucminas.pontomorto.DadosDeTeste;
import br.pucminas.pontomorto.TesteIntegracao;
import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusPedido;
import br.pucminas.pontomorto.repositorio.LocalRepository;
import br.pucminas.pontomorto.repositorio.PedidoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.servico.RoteiroService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN05, RN06 e RN07 aplicadas pelo serviço de roteiros (com banco). */
@TesteIntegracao
@WithUserDetails(DadosDeTeste.GERENTE)
@DisplayName("Regras do roteiro (RN05, RN06, RN07) e montagem a partir dos pedidos")
class RoteiroRegrasTest {

    @Autowired
    private RoteiroService servico;
    @Autowired
    private RoteiroRepository roteiros;
    @Autowired
    private PedidoRepository pedidos;
    @Autowired
    private LocalRepository locais;
    @Autowired
    private DadosDeTeste dados;
    @Autowired
    private EntityManager em;

    private Long base() {
        return locais.findByAtivoTrueOrderByNome().stream()
                .filter(l -> l.getNome().startsWith("Seg. Família")).findFirst().orElseThrow().getId();
    }

    private List<Long> pedidosPendentes(LocalDate dia) {
        return pedidos.pendentesDoDia(dia, null).stream().map(Pedido::getId).toList();
    }

    @Test
    @DisplayName("RN05: um motorista não pode ter dois roteiros na mesma data")
    void rn05BloqueiaDuplicidade() {
        Long joao = dados.motorista(DadosDeTeste.JOAO).getId();
        // João já tem roteiro hoje (dados de exemplo)
        assertThatThrownBy(() -> servico.montar(new RoteiroService.Montagem(joao, dados.hoje(), base(), List.of())))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já tem um roteiro");
    }

    @Test
    @DisplayName("RN05: em outra data, o mesmo motorista pode ter roteiro")
    void rn05OutraDataPode() {
        Long joao = dados.motorista(DadosDeTeste.JOAO).getId();
        Roteiro r = servico.montar(new RoteiroService.Montagem(joao, dados.hoje().plusDays(1), base(), List.of()));
        assertThat(r.getId()).isNotNull();
        assertThat(r.getMotorista().getId()).isEqualTo(joao);
    }

    @Test
    @DisplayName("Entrada de pedidos: cada endereço vira um ponto; pedidos no mesmo endereço dividem o ponto")
    void pedidosDoMesmoEnderecoNoMesmoPonto() {
        LocalDate amanha = dados.hoje().plusDays(1);
        List<Long> ids = pedidosPendentes(amanha); // 5 pedidos, 2 deles na Rua Peru, 55
        assertThat(ids).hasSize(5);
        Roteiro r = servico.montar(new RoteiroService.Montagem(dados.motorista(DadosDeTeste.MARIA).getId(), amanha, base(), ids));

        assertThat(r.getPontos()).hasSize(1 + 4); // partida + 4 endereços diferentes
        Ponto ruaPeru = r.getPontos().stream().filter(p -> p.getEndereco().startsWith("Rua Peru, 55")).findFirst().orElseThrow();
        assertThat(ruaPeru.getPedidos()).hasSize(2);
        assertThat(pedidos.findAllById(ids)).allMatch(p -> p.getStatus() == StatusPedido.EM_ROTEIRO);
        assertThat(r.getDistanciaEstimadaKm()).isPositive();
        assertThat(r.isDistanciaEstimadaAproximada()).isTrue(); // API de rotas desligada nos testes: linha reta
    }

    @Test
    @DisplayName("RN06: os pontos ficam em ordem 1, 2, 3... e continuam sequenciais ao reordenar e remover")
    void rn06OrdemSequencial() {
        LocalDate amanha = dados.hoje().plusDays(1);
        Roteiro r = servico.montar(new RoteiroService.Montagem(dados.motorista(DadosDeTeste.MARIA).getId(), amanha, base(),
                pedidosPendentes(amanha)));
        Long id = r.getId();
        assertThat(r.getPontos()).extracting(Ponto::getOrdem).containsExactly(1, 2, 3, 4, 5);

        Ponto terceiro = r.getPontos().get(2);
        servico.mover(id, terceiro.getId(), -1); // sobe para a posição 2
        Ponto ultimo = r.getPontos().get(4);
        int pedidosDoUltimo = ultimo.getPedidos().size();
        servico.removerPonto(id, ultimo.getId());
        em.flush();
        em.clear();

        Roteiro lido = roteiros.buscarComPontos(id).orElseThrow();
        assertThat(lido.getPontos()).extracting(Ponto::getOrdem).containsExactly(1, 2, 3, 4);
        assertThat(lido.getPontos().get(1).getId()).isEqualTo(terceiro.getId());
        // Os pedidos do ponto removido voltaram a ficar pendentes
        assertThat(pedidosDoUltimo).isPositive();
        assertThat(pedidos.pendentesDoDia(amanha, null)).hasSize(pedidosDoUltimo);
    }

    @Test
    @DisplayName("RN07: custo = (distância ÷ km/l × combustível) + (distância × custo por km), com a distância real")
    void rn07CustoDoRoteiro() {
        Roteiro a = dados.roteiroA();
        // João usa moto de 38 km/l; parâmetros padrão: R$ 6,29/l e R$ 0,45/km
        assertThat(a.getKmLitroUsado()).isEqualByComparingTo("38");
        BigDecimal km = a.getDistanciaRealKm();
        assertThat(km).isNotNull();
        BigDecimal esperado = CalculadoraCusto.custoDoTrajeto(km, new BigDecimal("38"), new BigDecimal("6.29"), new BigDecimal("0.45"));
        assertThat(a.getCustoEstimado()).isEqualByComparingTo(esperado);
        assertThat(a.getDistanciaParaCusto()).isEqualByComparingTo(km);
    }

    @Test
    @DisplayName("Roteiro concluído não pode ser reordenado")
    void concluidoNaoMuda() {
        Roteiro a = dados.roteiroA();
        Long segundo = a.getPontos().get(1).getId();
        assertThatThrownBy(() -> servico.mover(a.getId(), segundo, 1))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("concluído");
    }
}
