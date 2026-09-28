package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Motorista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MotoristaRepository extends JpaRepository<Motorista, Long> {

    Optional<Motorista> findByUsuarioId(Long usuarioId);

    /** Motoristas visíveis: todos (gerenteId nulo = administrador) ou só a equipe do gerente. */
    @Query("""
            select m from Motorista m
              left join fetch m.veiculo
              left join fetch m.gerente
             where (:gerenteId is null or m.gerente.id = :gerenteId)
             order by m.nome
            """)
    List<Motorista> listarVisiveis(@Param("gerenteId") Long gerenteId);

    @Query("""
            select m from Motorista m
             where m.ativo = true and m.anonimizado = false
               and (:gerenteId is null or m.gerente.id = :gerenteId)
             order by m.nome
            """)
    List<Motorista> listarAtivos(@Param("gerenteId") Long gerenteId);
}
