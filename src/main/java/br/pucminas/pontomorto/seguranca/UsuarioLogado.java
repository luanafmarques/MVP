package br.pucminas.pontomorto.seguranca;

import br.pucminas.pontomorto.dominio.Perfil;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Usuário autenticado, com o perfil (RNF04) e o vínculo com a ficha de motorista ou de gerente.
 */
public class UsuarioLogado implements UserDetails {

    private final Long id;
    private final String login;
    private final String senhaHash;
    private final Perfil perfil;
    private final boolean ativo;
    private final Long motoristaId;
    private final Long gerenteId;
    private final String nome;

    public UsuarioLogado(Long id, String login, String senhaHash, Perfil perfil, boolean ativo,
                         Long motoristaId, Long gerenteId, String nome) {
        this.id = id;
        this.login = login;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = ativo;
        this.motoristaId = motoristaId;
        this.gerenteId = gerenteId;
        this.nome = nome;
    }

    public boolean isAdmin() {
        return perfil == Perfil.ADMIN;
    }

    public boolean isGerente() {
        return perfil == Perfil.GERENTE;
    }

    public boolean isMotorista() {
        return perfil == Perfil.MOTORISTA;
    }

    /**
     * Filtro de equipe usado nas consultas: {@code null} para o administrador (vê tudo) e o id do gerente
     * para o gerente (vê só a própria equipe).
     */
    public Long escopoGerenteId() {
        return isAdmin() ? null : gerenteId;
    }

    public Long getId() {
        return id;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public Long getGerenteId() {
        return gerenteId;
    }

    public String getNome() {
        return nome;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
