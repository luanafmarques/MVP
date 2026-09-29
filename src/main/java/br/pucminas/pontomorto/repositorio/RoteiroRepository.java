package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoteiroRepository extends JpaRepository<Roteiro, Long> {

    /** RN05: já existe roteiro para esse motorista nessa data? */
    boolean existsByMotoristaIdAndData(Long motoristaId, LocalDate data);

    boolean existsByMotoristaId(Long motoristaId);

    @Query("""
            select distinct r from Roteiro r
              join fetch r.motorista m
              left join fetch r.pontos
             where r.id = :id
            """)
    Optional<Roteiro> buscarComPontos(@Param("id") Long id);

    @Query("""
            select distinct r from Roteiro r
              join fetch r.motorista m
              left join fetch r.pontos
             where m.id = :motoristaId and r.data = :data
            """)
    Optional<Roteiro> buscarDoMotoristaNaData(@Param("motoristaId") Long motoristaId, @Param("data") LocalDate data);

    @Query("""
            select r from Roteiro r
              join fetch r.motorista m
             where r.data between :inicio and :fim
               and (:gerenteId is null or m.gerente.id = :gerenteId)
               and (:motoristaId is null or m.id = :motoristaId)
             order by r.data desc, m.nome
            """)
    List<Roteiro> listar(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                         @Param("gerenteId") Long gerenteId, @Param("motoristaId") Long motoristaId);

    @Query("""
            select distinct r from Roteiro r
              join fetch r.motorista m
              left join fetch r.pontos
             where r.data = :data
               and r.status <> br.pucminas.pontomorto.dominio.StatusRoteiro.PLANEJADO
               and (:gerenteId is null or m.gerente.id = :gerenteId)
               and (:motoristaId is null or m.id = :motoristaId)
            """)
    List<Roteiro> listarComPontosNaData(@Param("data") LocalDate data, @Param("gerenteId") Long gerenteId,
                                        @Param("motoristaId") Long motoristaId);

    List<Roteiro> findByStatusIn(List<StatusRoteiro> status);

    /** Data mais recente com coleta, usada como padrão no recorte "Dia" do painel. */
    @Query("""
            select max(r.data) from Roteiro r
             where r.status <> br.pucminas.pontomorto.dominio.StatusRoteiro.PLANEJADO
               and r.data <= :ate
               and (:gerenteId is null or r.motorista.gerente.id = :gerenteId)
            """)
    Optional<LocalDate> ultimaDataComColeta(@Param("ate") LocalDate ate, @Param("gerenteId") Long gerenteId);

    // ---------- Consultas agregadas do painel (RF08 / RNF03) ----------

    @Query("""
            select new br.pucminas.pontomorto.repositorio.Agregados$TotalPorData(
                   r.data, count(r), sum(r.tempoTotalParadoSeg), sum(r.custoEstimado),
                   sum(r.distanciaEstimadaKm), sum(r.distanciaRealKm),
                   sum(case when r.distanciaRealKm is not null then r.distanciaEstimadaKm end),
                   sum(coalesce(r.distanciaRealKm, r.distanciaEstimadaKm)),
                   sum(case when r.kmLitroUsado > 0 then coalesce(r.distanciaRealKm, r.distanciaEstimadaKm) end),
                   sum(case when r.kmLitroUsado > 0
                            then coalesce(r.distanciaRealKm, r.distanciaEstimadaKm) / r.kmLitroUsado end))
              from Roteiro r
             where r.data between :inicio and :fim
               and r.status <> br.pucminas.pontomorto.dominio.StatusRoteiro.PLANEJADO
               and (:gerenteId is null or r.motorista.gerente.id = :gerenteId)
               and (:motoristaId is null or r.motorista.id = :motoristaId)
             group by r.data
             order by r.data
            """)
    List<Agregados.TotalPorData> totaisPorData(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                                               @Param("gerenteId") Long gerenteId,
                                               @Param("motoristaId") Long motoristaId);

    @Query("""
            select new br.pucminas.pontomorto.repositorio.Agregados$TotalPorMes(
                   year(r.data), month(r.data), count(r), sum(r.tempoTotalParadoSeg), sum(r.custoEstimado),
                   sum(r.distanciaEstimadaKm), sum(r.distanciaRealKm),
                   sum(case when r.distanciaRealKm is not null then r.distanciaEstimadaKm end),
                   sum(coalesce(r.distanciaRealKm, r.distanciaEstimadaKm)),
                   sum(case when r.kmLitroUsado > 0 then coalesce(r.distanciaRealKm, r.distanciaEstimadaKm) end),
                   sum(case when r.kmLitroUsado > 0
                            then coalesce(r.distanciaRealKm, r.distanciaEstimadaKm) / r.kmLitroUsado end))
              from Roteiro r
             where r.data between :inicio and :fim
               and r.status <> br.pucminas.pontomorto.dominio.StatusRoteiro.PLANEJADO
               and (:gerenteId is null or r.motorista.gerente.id = :gerenteId)
               and (:motoristaId is null or r.motorista.id = :motoristaId)
             group by year(r.data), month(r.data)
             order by year(r.data), month(r.data)
            """)
    List<Agregados.TotalPorMes> totaisPorMes(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                                             @Param("gerenteId") Long gerenteId,
                                             @Param("motoristaId") Long motoristaId);

    @Query("""
            select new br.pucminas.pontomorto.repositorio.Agregados$TotalPorMotorista(
                   m.id, m.nome, count(r), sum(r.tempoTotalParadoSeg), sum(r.custoEstimado))
              from Roteiro r join r.motorista m
             where r.data between :inicio and :fim
               and r.status <> br.pucminas.pontomorto.dominio.StatusRoteiro.PLANEJADO
               and (:gerenteId is null or m.gerente.id = :gerenteId)
               and (:motoristaId is null or m.id = :motoristaId)
             group by m.id, m.nome
             order by sum(r.tempoTotalParadoSeg) desc
            """)
    List<Agregados.TotalPorMotorista> totaisPorMotorista(@Param("inicio") LocalDate inicio,
                                                         @Param("fim") LocalDate fim,
                                                         @Param("gerenteId") Long gerenteId,
                                                         @Param("motoristaId") Long motoristaId);
}
