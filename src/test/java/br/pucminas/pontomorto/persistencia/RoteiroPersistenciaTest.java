package br.pucminas.pontomorto.persistencia;

import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Camada de persistência: migrações Flyway + mapeamento JPA + restrições do banco
 * que protegem RN05, RN06 e as validações de horário.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Persistência de roteiros e pontos")
class RoteiroPersistenciaTest {

    @Autowired
    private RoteiroRepository roteiros;

    @Autowired
    private MotoristaRepository motoristas;

    @Autowired
    private EntityManager em;

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 21, 7, 0);

    private Motorista novoMotorista() {
        return motoristas.save(new Motorista("Teste", "31 99999-0000", "12345678901"));
    }

    @Test
    @DisplayName("RN05: o banco bloqueia dois roteiros do mesmo motorista na mesma data")
    void rn05RestricaoUnica() {
        Motorista m = novoMotorista();
        LocalDate dia = LocalDate.of(2026, 9, 21);
        roteiros.saveAndFlush(new Roteiro(dia, m, AGORA));
        assertThat(roteiros.existsByMotoristaIdAndData(m.getId(), dia)).isTrue();

        assertThatThrownBy(() -> roteiros.saveAndFlush(new Roteiro(dia, m, AGORA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN06: o banco bloqueia dois pontos com a mesma ordem no roteiro")
    void rn06OrdemUnica() {
        Roteiro r = new Roteiro(LocalDate.of(2026, 9, 22), novoMotorista(), AGORA);
        r.adicionarPonto(new Ponto("Base", null, null));
        Ponto segundo = r.adicionarPonto(new Ponto("Rua Peru, 55", null, null));
        roteiros.saveAndFlush(r);

        segundo.setOrdem(1);
        assertThatThrownBy(() -> roteiros.saveAndFlush(r)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa saída antes da chegada")
    void saidaAntesDaChegada() {
        Roteiro r = new Roteiro(LocalDate.of(2026, 9, 23), novoMotorista(), AGORA);
        r.adicionarPonto(new Ponto("Base", null, null));
        Ponto p = r.adicionarPonto(new Ponto("Rua Peru, 55", BigDecimal.ONE, BigDecimal.ONE));
        p.setChegada(AGORA.plusHours(2));
        p.setSaida(AGORA.plusHours(1));
        assertThatThrownBy(() -> roteiros.saveAndFlush(r)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("grava e lê o roteiro com os pontos em ordem")
    void gravaELe() {
        Roteiro r = new Roteiro(LocalDate.of(2026, 9, 24), novoMotorista(), AGORA);
        r.adicionarPonto(new Ponto("Base", null, null));
        r.adicionarPonto(new Ponto("Rua Peru, 55", null, null));
        r.adicionarPonto(new Ponto("Rua X, 5", null, null));
        r.definirHodometro(new BigDecimal("1000.0"), new BigDecimal("1038.4"));
        Long id = roteiros.saveAndFlush(r).getId();
        em.clear();

        Roteiro lido = roteiros.buscarComPontos(id).orElseThrow();
        assertThat(lido.getPontos()).extracting(Ponto::getOrdem).containsExactly(1, 2, 3);
        assertThat(lido.getPontos()).extracting(Ponto::getEndereco).containsExactly("Base", "Rua Peru, 55", "Rua X, 5");
        assertThat(lido.getDistanciaRealKm()).isEqualByComparingTo("38.4");
    }
}
