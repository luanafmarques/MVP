package br.pucminas.pontomorto.seguranca;

import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Roteiro;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Controle de acesso por perfil e por equipe (RNF04).
 * <ul>
 *   <li>Motorista: só o próprio roteiro.</li>
 *   <li>Gerente: só os motoristas da sua equipe.</li>
 *   <li>Administrador: tudo.</li>
 * </ul>
 */
@Component
public class ControleAcesso {

    public Optional<UsuarioLogado> usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioLogado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public UsuarioLogado exigirUsuario() {
        return usuarioAtual().orElseThrow(() -> new AccessDeniedException("Faça login para continuar."));
    }

    /** Login de quem está fazendo a alteração, para a auditoria. */
    public String loginAtual() {
        return usuarioAtual().map(UsuarioLogado::getUsername).orElse("sistema");
    }

    public boolean podeVer(Motorista motorista, UsuarioLogado usuario) {
        if (usuario.isAdmin()) {
            return true;
        }
        if (usuario.isGerente()) {
            return motorista.getGerente() != null && Objects.equals(motorista.getGerente().getId(), usuario.getGerenteId());
        }
        return Objects.equals(motorista.getId(), usuario.getMotoristaId());
    }

    public void verificar(Motorista motorista) {
        if (!podeVer(motorista, exigirUsuario())) {
            throw new AccessDeniedException("Você não tem acesso a este motorista.");
        }
    }

    public void verificar(Roteiro roteiro) {
        if (!podeVer(roteiro.getMotorista(), exigirUsuario())) {
            throw new AccessDeniedException("Você não tem acesso a este roteiro.");
        }
    }

    /** Gerente ou administrador (quem gerencia a equipe). */
    public void exigirGestor() {
        UsuarioLogado usuario = exigirUsuario();
        if (usuario.isMotorista()) {
            throw new AccessDeniedException("Apenas gerentes e administradores podem fazer isso.");
        }
    }
}
