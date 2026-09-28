package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Gerente;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Perfil;
import br.pucminas.pontomorto.dominio.Usuario;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.GerenteRepository;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.PedidoRepository;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** RF02: cadastro de gerentes, coordenadores e donos de transportadora, com a equipe sob responsabilidade. */
@Service
public class GerenteService {

    public record Dados(String nome, String telefone, String email, String senha, List<Long> equipeIds) {
    }

    private final GerenteRepository gerentes;
    private final MotoristaRepository motoristas;
    private final UsuarioRepository usuarios;
    private final PedidoRepository pedidos;
    private final PasswordEncoder senhas;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public GerenteService(GerenteRepository gerentes, MotoristaRepository motoristas, UsuarioRepository usuarios,
                          PedidoRepository pedidos, PasswordEncoder senhas, AuditoriaService auditoria, Clock relogio) {
        this.gerentes = gerentes;
        this.motoristas = motoristas;
        this.usuarios = usuarios;
        this.pedidos = pedidos;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Gerente> listar() {
        return gerentes.findAllByOrderByNome();
    }

    @Transactional(readOnly = true)
    public Gerente buscar(Long id) {
        return gerentes.findById(id).orElseThrow(() -> new NaoEncontradoException("Gerente não encontrado."));
    }

    /** O e-mail do gerente também é o login dele. */
    @Transactional
    public Gerente criar(Dados dados) {
        validar(dados);
        String login = dados.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByLoginIgnoreCase(login)) {
            throw new RegraNegocioException("Já existe um usuário com o login " + login + ".");
        }
        if (dados.senha() == null || dados.senha().length() < 6) {
            throw new RegraNegocioException("A senha inicial precisa ter pelo menos 6 caracteres.");
        }
        Usuario usuario = usuarios.save(new Usuario(login, senhas.encode(dados.senha()), Perfil.GERENTE,
                LocalDateTime.now(relogio)));
        Gerente gerente = new Gerente(dados.nome().trim(), MotoristaService.vazioParaNulo(dados.telefone()), login);
        gerente.setUsuario(usuario);
        gerentes.save(gerente);
        definirEquipe(gerente, dados.equipeIds());
        auditoria.criacao("Gerente", gerente.getId(), "Gerente " + gerente.getNome(), gerente.getId());
        return gerente;
    }

    @Transactional
    public Gerente atualizar(Long id, Dados dados) {
        validar(dados);
        Gerente gerente = buscar(id);
        String login = dados.email().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = gerente.getUsuario();
        if (usuario != null && !usuario.getLogin().equalsIgnoreCase(login) && usuarios.existsByLoginIgnoreCase(login)) {
            throw new RegraNegocioException("Já existe um usuário com o login " + login + ".");
        }
        String descricao = "Gerente " + gerente.getNome();
        auditoria.alteracao("Gerente", id, descricao, "Nome", gerente.getNome(), dados.nome().trim(), null, id);
        auditoria.alteracao("Gerente", id, descricao, "Telefone", gerente.getTelefone(), MotoristaService.vazioParaNulo(dados.telefone()), null, id);
        auditoria.alteracao("Gerente", id, descricao, "E-mail", gerente.getEmail(), login, null, id);
        gerente.setNome(dados.nome().trim());
        gerente.setTelefone(MotoristaService.vazioParaNulo(dados.telefone()));
        gerente.setEmail(login);
        if (usuario != null) {
            usuario.setLogin(login);
            if (dados.senha() != null && !dados.senha().isBlank()) {
                if (dados.senha().length() < 6) {
                    throw new RegraNegocioException("A nova senha precisa ter pelo menos 6 caracteres.");
                }
                usuario.setSenhaHash(senhas.encode(dados.senha()));
            }
        }
        definirEquipe(gerente, dados.equipeIds());
        return gerente;
    }

    @Transactional
    public void excluir(Long id) {
        Gerente gerente = buscar(id);
        if (!motoristas.listarVisiveis(id).isEmpty()) {
            throw new RegraNegocioException("Tire os motoristas da equipe antes de excluir o gerente.");
        }
        if (!pedidos.pesquisar(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1), null, id).isEmpty()) {
            throw new RegraNegocioException("Esse gerente tem pedidos cadastrados e não pode ser excluído.");
        }
        Usuario usuario = gerente.getUsuario();
        String descricao = "Gerente " + gerente.getNome();
        gerentes.delete(gerente);
        if (usuario != null) {
            usuarios.delete(usuario);
        }
        auditoria.exclusao("Gerente", id, descricao, null, null);
    }

    /** A equipe é a lista de motoristas marcados; quem saiu da lista fica sem gerente. */
    private void definirEquipe(Gerente gerente, List<Long> equipeIds) {
        Set<Long> novos = equipeIds == null ? Set.of() : new HashSet<>(equipeIds);
        for (Motorista m : motoristas.listarVisiveis(null)) {
            boolean estava = m.getGerente() != null && Objects.equals(m.getGerente().getId(), gerente.getId());
            boolean fica = novos.contains(m.getId());
            if (estava != fica) {
                auditoria.alteracao("Motorista", m.getId(), "Motorista " + m.getNome(), "Equipe (gerente)",
                        m.getGerente() == null ? null : m.getGerente().getNome(),
                        fica ? gerente.getNome() : null, null, gerente.getId());
                m.setGerente(fica ? gerente : null);
            }
        }
    }

    private static void validar(Dados dados) {
        if (dados.nome() == null || dados.nome().isBlank()) {
            throw new RegraNegocioException("Informe o nome.");
        }
        if (dados.email() == null || !dados.email().contains("@")) {
            throw new RegraNegocioException("Informe um e-mail válido (ele também é o login).");
        }
    }
}
