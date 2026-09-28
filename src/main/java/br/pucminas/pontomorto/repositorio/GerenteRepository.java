package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Gerente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GerenteRepository extends JpaRepository<Gerente, Long> {

    Optional<Gerente> findByUsuarioId(Long usuarioId);

    List<Gerente> findAllByOrderByNome();
}
