package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.SolicitacaoTeste;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoTesteRepository extends JpaRepository<SolicitacaoTeste, Long> {
}
