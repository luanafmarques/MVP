package br.pucminas.pontomorto.repositorio;

import br.pucminas.pontomorto.dominio.Consentimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentimentoRepository extends JpaRepository<Consentimento, Long> {

    boolean existsByUsuarioIdAndVersaoAviso(Long usuarioId, String versaoAviso);

    Optional<Consentimento> findFirstByUsuarioIdOrderByAceitoEmDesc(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
