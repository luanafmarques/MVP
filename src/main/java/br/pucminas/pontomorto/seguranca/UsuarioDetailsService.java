package br.pucminas.pontomorto.seguranca;

import br.pucminas.pontomorto.dominio.Gerente;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Usuario;
import br.pucminas.pontomorto.repositorio.GerenteRepository;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;
    private final MotoristaRepository motoristas;
    private final GerenteRepository gerentes;

    public UsuarioDetailsService(UsuarioRepository usuarios, MotoristaRepository motoristas, GerenteRepository gerentes) {
        this.usuarios = usuarios;
        this.motoristas = motoristas;
        this.gerentes = gerentes;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) {
        Usuario usuario = usuarios.findByLoginIgnoreCase(login.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário ou senha inválidos."));
        return paraUsuarioLogado(usuario);
    }

    public UsuarioLogado paraUsuarioLogado(Usuario usuario) {
        Optional<Motorista> motorista = motoristas.findByUsuarioId(usuario.getId());
        Optional<Gerente> gerente = gerentes.findByUsuarioId(usuario.getId());
        String nome = motorista.map(Motorista::getNome)
                .or(() -> gerente.map(Gerente::getNome))
                .orElse("Administrador");
        return new UsuarioLogado(usuario.getId(), usuario.getLogin(), usuario.getSenhaHash(), usuario.getPerfil(),
                usuario.isAtivo(),
                motorista.map(Motorista::getId).orElse(null),
                gerente.map(Gerente::getId).orElse(null),
                nome);
    }
}
