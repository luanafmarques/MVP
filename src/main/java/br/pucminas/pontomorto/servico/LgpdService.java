package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Consentimento;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Usuario;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.ConsentimentoRepository;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * RNF06 (LGPD):
 * <ul>
 *   <li>aviso de privacidade e registro de consentimento no primeiro acesso do motorista;</li>
 *   <li>anonimização ou exclusão dos dados pessoais de um profissional pelo administrador.</li>
 * </ul>
 * O histórico de tempos e endereços de entrega continua disponível depois da anonimização, sem identificar a pessoa.
 */
@Service
public class LgpdService {

    /** Mude a versão quando o texto do aviso mudar: todos os motoristas precisarão aceitar de novo. */
    public static final String VERSAO_AVISO = "1.0";

    private final ConsentimentoRepository consentimentos;
    private final UsuarioRepository usuarios;
    private final MotoristaRepository motoristas;
    private final RoteiroRepository roteiros;
    private final MotoristaService motoristaService;
    private final PasswordEncoder senhas;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    public LgpdService(ConsentimentoRepository consentimentos, UsuarioRepository usuarios,
                       MotoristaRepository motoristas, RoteiroRepository roteiros, MotoristaService motoristaService,
                       PasswordEncoder senhas, AuditoriaService auditoria, Clock relogio) {
        this.consentimentos = consentimentos;
        this.usuarios = usuarios;
        this.motoristas = motoristas;
        this.roteiros = roteiros;
        this.motoristaService = motoristaService;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public boolean aceitouAvisoAtual(Long usuarioId) {
        return consentimentos.existsByUsuarioIdAndVersaoAviso(usuarioId, VERSAO_AVISO);
    }

    @Transactional(readOnly = true)
    public Optional<Consentimento> ultimoConsentimento(Long usuarioId) {
        return consentimentos.findFirstByUsuarioIdOrderByAceitoEmDesc(usuarioId);
    }

    @Transactional
    public void registrarConsentimento(Long usuarioId) {
        if (aceitouAvisoAtual(usuarioId)) {
            return;
        }
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        consentimentos.save(new Consentimento(usuario, VERSAO_AVISO, LocalDateTime.now(relogio)));
    }

    /**
     * Remove os dados pessoais (nome, telefone, documento e login) e mantém os roteiros para as estatísticas.
     * O acesso do profissional é desativado.
     */
    @Transactional
    public void anonimizar(Long motoristaId) {
        Motorista motorista = motoristas.findById(motoristaId)
                .orElseThrow(() -> new NaoEncontradoException("Motorista não encontrado."));
        if (motorista.isAnonimizado()) {
            throw new RegraNegocioException("Os dados deste profissional já foram anonimizados.");
        }
        Long gerente = motorista.getGerente() == null ? null : motorista.getGerente().getId();
        motorista.setNome("Profissional anonimizado #" + motorista.getId());
        motorista.setTelefone(null);
        motorista.setDocumento(null);
        motorista.setAnonimizado(true);
        motorista.setAtivo(false);
        Usuario usuario = motorista.getUsuario();
        if (usuario != null) {
            usuario.setLogin("anonimizado-" + motorista.getId() + "@pontomorto.invalid");
            usuario.setSenhaHash(senhas.encode(UUID.randomUUID().toString()));
            usuario.setAtivo(false);
        }
        // A auditoria registra o fato, mas não guarda os dados pessoais removidos.
        auditoria.alteracao("Motorista", motoristaId, "Profissional #" + motoristaId, "Dados pessoais",
                "(nome, telefone, documento e login)", "(anonimizados)", "Pedido LGPD atendido pelo administrador", gerente);
    }

    /**
     * Exclui de vez os dados pessoais. Se houver roteiros no histórico, o cadastro não pode sumir
     * (os tempos continuam sendo usados), então os dados são anonimizados.
     *
     * @return verdadeiro se o cadastro foi excluído; falso se foi anonimizado
     */
    @Transactional
    public boolean excluirDadosPessoais(Long motoristaId) {
        if (roteiros.existsByMotoristaId(motoristaId)) {
            anonimizar(motoristaId);
            return false;
        }
        motoristaService.excluir(motoristaId);
        return true;
    }
}
