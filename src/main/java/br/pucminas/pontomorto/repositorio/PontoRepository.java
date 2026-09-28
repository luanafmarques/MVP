package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Ponto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PontoRepository extends JpaRepository<Ponto, Long> {

    /** Ranking de endereços com mais tempo parado no período (a partida não entra: RN01). */
    @Query("""
            select new br.pucminas.pontomorto.repositorio.Agregados$TempoPorEndereco(
                   p.endereco, count(p), sum(p.tempoParadoSeg), max(p.tempoParadoSeg))
              from Ponto p join p.roteiro r
             where r.data between :inicio and :fim
               and p.ordem > 1 and p.tempoParadoSeg is not null
               and (:gerenteId is null or r.motorista.gerente.id = :gerenteId)
               and (:motoristaId is null or r.motorista.id = :motoristaId)
             group by p.endereco
             order by sum(p.tempoParadoSeg) desc, p.endereco
            """)
    List<Agregados.TempoPorEndereco> rankingPorEndereco(@Param("inicio") LocalDate inicio,
                                                        @Param("fim") LocalDate fim,
                                                        @Param("gerenteId") Long gerenteId,
                                                        @Param("motoristaId") Long motoristaId,
                                                        Pageable limite);

    /** Histórico de pontos (RF07): todos os pontos do período, com endereço e horários. */
    @Query(value = """
            select p from Ponto p
              join fetch p.roteiro r
              join fetch r.motorista m
             where r.data between :inicio and :fim
               and (:gerenteId is null or m.gerente.id = :gerenteId)
               and (:motoristaId is null or m.id = :motoristaId)
               and (:somenteParadas = false or (p.ordem > 1 and p.tempoParadoSeg is not null))
             order by r.data desc, m.nome, p.ordem
            """,
            countQuery = """
            select count(p) from Ponto p join p.roteiro r join r.motorista m
             where r.data between :inicio and :fim
               and (:gerenteId is null or m.gerente.id = :gerenteId)
               and (:motoristaId is null or m.id = :motoristaId)
               and (:somenteParadas = false or (p.ordem > 1 and p.tempoParadoSeg is not null))
            """)
    Page<Ponto> historico(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                          @Param("gerenteId") Long gerenteId, @Param("motoristaId") Long motoristaId,
                          @Param("somenteParadas") boolean somenteParadas, Pageable pageable);

    /** Mesmo filtro do histórico, sem paginação, para exportar CSV/PDF (RF12). */
    @Query("""
            select p from Ponto p
              join fetch p.roteiro r
              join fetch r.motorista m
             where r.data between :inicio and :fim
               and (:gerenteId is null or m.gerente.id = :gerenteId)
               and (:motoristaId is null or m.id = :motoristaId)
               and (:somenteParadas = false or (p.ordem > 1 and p.tempoParadoSeg is not null))
             order by r.data desc, m.nome, p.ordem
            """)
    List<Ponto> historicoCompleto(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                                  @Param("gerenteId") Long gerenteId, @Param("motoristaId") Long motoristaId,
                                  @Param("somenteParadas") boolean somenteParadas);
}
