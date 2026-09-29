package br.pucminas.pontomorto;

import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Atalhos para achar os dados de exemplo nos testes. */
@Component
public class DadosDeTeste {

    public static final String ADMIN = "admin@pontomorto.com.br";
    public static final String GERENTE = "gerente@pontomorto.com.br";
    public static final String JOAO = "joao@pontomorto.com.br";
    public static final String MARIA = "maria@pontomorto.com.br";

    private final UsuarioRepository usuarios;
    private final MotoristaRepository motoristas;
    private final RoteiroRepository roteiros;
    private final Clock relogio;

    public DadosDeTeste(UsuarioRepository usuarios, MotoristaRepository motoristas, RoteiroRepository roteiros, Clock relogio) {
        this.usuarios = usuarios;
        this.motoristas = motoristas;
        this.roteiros = roteiros;
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    public LocalDate ontem() {
        return hoje().minusDays(1);
    }

    public Motorista motorista(String login) {
        Long usuarioId = usuarios.findByLoginIgnoreCase(login).orElseThrow().getId();
        return motoristas.findByUsuarioId(usuarioId).orElseThrow();
    }

    /** Roteiro A do enunciado (João, ontem). */
    public Roteiro roteiroA() {
        return roteiroDe(JOAO, ontem());
    }

    public Roteiro roteiroDe(String login, LocalDate data) {
        return roteiros.buscarDoMotoristaNaData(motorista(login).getId(), data).orElseThrow();
    }
}
