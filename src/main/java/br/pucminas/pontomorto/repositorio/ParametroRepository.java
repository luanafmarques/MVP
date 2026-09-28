package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Parametro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroRepository extends JpaRepository<Parametro, Long> {
}
