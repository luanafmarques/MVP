package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Gerente;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Perfil;
import br.pucminas.pontomorto.dominio.TipoVeiculo;
import br.pucminas.pontomorto.dominio.Usuario;
import br.pucminas.pontomorto.dominio.Veiculo;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.ConsentimentoRepository;
import br.pucminas.pontomorto.repositorio.GerenteRepository;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import br.pucminas.pontomorto.repositorio.VeiculoRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import br.pucminas.pontomorto.seguranca.UsuarioLogado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** RF01: cadastro de motoristas e motoboys (criar, listar, editar e excluir), com o veículo e o login. */
@Service
public class MotoristaService {

    public record Dados(String nome, String telefone, String documento, Long gerenteId,
                        String placa, TipoVeiculo tipoVeiculo, String modeloVeiculo, BigDecimal rendimentoKmLitro,
                        String login, String senha) {
    }

    private final MotoristaRepository motoristas;
    private final VeiculoRepository veiculos;
    private final UsuarioRepository usuarios;
    private final GerenteRepository gerentes;
    private final RoteiroRepository roteiros;
    private final ConsentimentoRepository consentimentos;
    private final PasswordEncoder senhas;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;
    private final Clock relogio;

    public MotoristaService(MotoristaRepository motoristas, VeiculoRepository veiculos, UsuarioRepository usuarios,
                            GerenteRepository gerentes, RoteiroRepository roteiros,
                            ConsentimentoRepository consentimentos, PasswordEncoder senhas,
                            AuditoriaService auditoria, ControleAcesso acesso, Clock relogio) {
        this.motoristas = motoristas;
        this.veiculos = veiculos;
        this.usuarios = usuarios;
        this.gerentes = gerentes;
        this.roteiros = roteiros;
        this.consentimentos = consentimentos;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.acesso = acesso;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Motorista> listar() {
        return motoristas.listarVisiveis(acesso.exigirUsuario().escopoGerenteId());
    }

    @Transactional(readOnly = true)
    public List<Motorista> listarAtivos() {
        return motoristas.listarAtivos(acesso.exigirUsuario().escopoGerenteId());
    }

    @Transactional(readOnly = true)
    public Motorista buscar(Long id) {
        Motorista motorista = motoristas.findById(id).orElseThrow(() -> new NaoEncontradoException("Motorista não encontrado."));
        acesso.verificar(motorista);
        return motorista;
    }

    @Transactional
    public Motorista criar(Dados dados) {
        acesso.exigirGestor();
        validar(dados, true);
        String login = dados.login().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByLoginIgnoreCase(login)) {
            throw new RegraNegocioException("Já existe um usuário com o login " + login + ".");
        }
        Usuario usuario = usuarios.save(new Usuario(login, senhas.encode(dados.senha()), Perfil.MOTORISTA,
                LocalDateTime.now(relogio)));

        Motorista motorista = new Motorista(dados.nome().trim(), vazioParaNulo(dados.telefone()), soDigitos(dados.documento()));
        motorista.setUsuario(usuario);
        motorista.setGerente(gerenteDestino(dados.gerenteId()));
        motorista.setVeiculo(veiculo(dados, null));
        motoristas.save(motorista);
        auditoria.criacao("Motorista", motorista.getId(), "Motorista " + motorista.getNome(), gerenteId(motorista));
        return motorista;
    }

    @Transactional
    public Motorista atualizar(Long id, Dados dados) {
        acesso.exigirGestor();
        Motorista motorista = buscar(id);
        if (motorista.isAnonimizado()) {
            throw new RegraNegocioException("Os dados deste profissional foram anonimizados e não podem ser editados.");
        }
        validar(dados, false);
        String descricao = "Motorista " + motorista.getNome();
        Long gerenteAntes = gerenteId(motorista);

        auditoria.alteracao("Motorista", id, descricao, "Nome", motorista.getNome(), dados.nome().trim(), null, gerenteAntes);
        auditoria.alteracao("Motorista", id, descricao, "Telefone", motorista.getTelefone(), vazioParaNulo(dados.telefone()), null, gerenteAntes);
        // Documento: a auditoria guarda só a versão mascarada (LGPD).
        auditoria.alteracao("Motorista", id, descricao, "Documento", motorista.getDocumentoMascarado(),
                Motorista.mascarar(soDigitos(dados.documento())), null, gerenteAntes);

        motorista.setNome(dados.nome().trim());
        motorista.setTelefone(vazioParaNulo(dados.telefone()));
        motorista.setDocumento(soDigitos(dados.documento()));
        Gerente novoGerente = gerenteDestino(dados.gerenteId());
        auditoria.alteracao("Motorista", id, descricao, "Equipe (gerente)",
                motorista.getGerente() == null ? null : motorista.getGerente().getNome(),
                novoGerente == null ? null : novoGerente.getNome(), null, gerenteAntes);
        motorista.setGerente(novoGerente);
        motorista.setVeiculo(veiculo(dados, motorista.getVeiculo()));

        if (dados.senha() != null && !dados.senha().isBlank()) {
            motorista.getUsuario().setSenhaHash(senhas.encode(dados.senha()));
            auditoria.alteracao("Motorista", id, descricao, "Senha", "(anterior)", "(nova senha definida)", null, gerenteAntes);
        }
        return motorista;
    }

    /** Exclui o cadastro. Se o motorista já tem roteiros, o histórico fica e a saída é anonimizar (RNF06). */
    @Transactional
    public void excluir(Long id) {
        acesso.exigirGestor();
        Motorista motorista = buscar(id);
        if (roteiros.existsByMotoristaId(id)) {
            throw new RegraNegocioException("Esse motorista tem roteiros no histórico e não pode ser excluído. "
                    + "Desative o cadastro ou peça ao administrador para anonimizar os dados pessoais.");
        }
        Usuario usuario = motorista.getUsuario();
        String descricao = "Motorista " + motorista.getNome();
        Long gerente = gerenteId(motorista);
        motoristas.delete(motorista);
        if (usuario != null) {
            consentimentos.deleteByUsuarioId(usuario.getId());
            usuarios.delete(usuario);
        }
        auditoria.exclusao("Motorista", id, descricao, null, gerente);
    }

    @Transactional
    public void alterarAtivo(Long id, boolean ativo) {
        acesso.exigirGestor();
        Motorista motorista = buscar(id);
        if (motorista.isAnonimizado()) {
            throw new RegraNegocioException("Cadastro anonimizado não pode ser reativado.");
        }
        auditoria.alteracao("Motorista", id, "Motorista " + motorista.getNome(), "Ativo",
                motorista.isAtivo() ? "Sim" : "Não", ativo ? "Sim" : "Não", null, gerenteId(motorista));
        motorista.setAtivo(ativo);
        if (motorista.getUsuario() != null) {
            motorista.getUsuario().setAtivo(ativo);
        }
    }

    // ---------- Apoio ----------

    /** O gerente sempre cadastra na própria equipe; o administrador escolhe a equipe. */
    private Gerente gerenteDestino(Long gerenteIdInformado) {
        UsuarioLogado usuario = acesso.exigirUsuario();
        Long id = usuario.isGerente() ? usuario.getGerenteId() : gerenteIdInformado;
        return id == null ? null : gerentes.findById(id).orElseThrow(() -> new NaoEncontradoException("Gerente não encontrado."));
    }

    private Veiculo veiculo(Dados dados, Veiculo atual) {
        if (dados.placa() == null || dados.placa().isBlank()) {
            return atual;
        }
        String placa = dados.placa().trim().toUpperCase(Locale.ROOT).replace(" ", "");
        Veiculo veiculo = veiculos.findByPlacaIgnoreCase(placa).orElse(null);
        if (veiculo == null) {
            veiculo = atual != null && atual.getPlaca().equalsIgnoreCase(placa) ? atual
                    : new Veiculo(placa, dados.tipoVeiculo(), vazioParaNulo(dados.modeloVeiculo()), dados.rendimentoKmLitro());
        }
        veiculo.setPlaca(placa);
        veiculo.setTipo(dados.tipoVeiculo());
        veiculo.setModelo(vazioParaNulo(dados.modeloVeiculo()));
        veiculo.setRendimentoKmLitro(dados.rendimentoKmLitro());
        return veiculos.save(veiculo);
    }

    private static void validar(Dados dados, boolean novo) {
        if (dados.nome() == null || dados.nome().isBlank()) {
            throw new RegraNegocioException("Informe o nome.");
        }
        if (dados.placa() != null && !dados.placa().isBlank()) {
            if (dados.tipoVeiculo() == null) {
                throw new RegraNegocioException("Escolha o tipo do veículo.");
            }
            if (dados.rendimentoKmLitro() == null || dados.rendimentoKmLitro().signum() <= 0) {
                throw new RegraNegocioException("Informe o rendimento do veículo em km por litro (maior que zero).");
            }
        }
        if (novo) {
            if (dados.login() == null || dados.login().isBlank()) {
                throw new RegraNegocioException("Informe o login (e-mail) de acesso do motorista.");
            }
            if (dados.senha() == null || dados.senha().length() < 6) {
                throw new RegraNegocioException("A senha inicial precisa ter pelo menos 6 caracteres.");
            }
        } else if (dados.senha() != null && !dados.senha().isBlank() && dados.senha().length() < 6) {
            throw new RegraNegocioException("A nova senha precisa ter pelo menos 6 caracteres.");
        }
    }

    static String soDigitos(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        return documento.replaceAll("\\D", "");
    }

    static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static Long gerenteId(Motorista motorista) {
        return motorista.getGerente() == null ? null : motorista.getGerente().getId();
    }
}
