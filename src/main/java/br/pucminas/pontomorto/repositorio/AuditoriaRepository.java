package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    @Query("""
            select a from Auditoria a
             where a.dataHora >= :inicio and a.dataHora < :fim
               and (:gerenteId is null or a.gerenteId = :gerenteId)
               and (:entidade is null or a.entidade = :entidade)
             order by a.dataHora desc, a.id desc
            """)
    Page<Auditoria> pesquisar(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
                              @Param("gerenteId") Long gerenteId, @Param("entidade") String entidade,
                              Pageable pageable);

    List<Auditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, Long entidadeId);
}
