package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Local;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocalRepository extends JpaRepository<Local, Long> {

    List<Local> findByAtivoTrueOrderByNome();

    List<Local> findAllByOrderByNome();
}
