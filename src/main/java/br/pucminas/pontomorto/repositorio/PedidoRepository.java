package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    boolean existsByNumeroIgnoreCase(String numero);

    @Query("""
            select p from Pedido p
             where p.dataPrevista between :inicio and :fim
               and (:status is null or p.status = :status)
               and (:gerenteId is null or p.gerente.id = :gerenteId)
             order by p.dataPrevista desc, p.numero
            """)
    List<Pedido> pesquisar(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                           @Param("status") StatusPedido status, @Param("gerenteId") Long gerenteId);

    /** Pedidos do dia que ainda não entraram em roteiro, para montar o roteiro. */
    @Query("""
            select p from Pedido p
             where p.dataPrevista = :data
               and p.status = br.pucminas.pontomorto.dominio.StatusPedido.PENDENTE
               and (:gerenteId is null or p.gerente.id = :gerenteId)
             order by p.enderecoEntrega, p.numero
            """)
    List<Pedido> pendentesDoDia(@Param("data") LocalDate data, @Param("gerenteId") Long gerenteId);

    @Query("select p from Pedido p where p.ponto.roteiro.id = :roteiroId order by p.numero")
    List<Pedido> doRoteiro(@Param("roteiroId") Long roteiroId);
}
