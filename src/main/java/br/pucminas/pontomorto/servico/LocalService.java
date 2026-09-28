package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Local;
import br.pucminas.pontomorto.regras.RegraNegocioException;
import br.pucminas.pontomorto.repositorio.LocalRepository;
import br.pucminas.pontomorto.seguranca.ControleAcesso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** RF03: cadastro de pontos com endereço e coordenadas (geocodificação automática com ajuste manual). */
@Service
public class LocalService {

    public record Dados(String nome, String endereco, BigDecimal latitude, BigDecimal longitude) {
    }

    private final LocalRepository locais;
    private final GeocodificacaoService geocodificacao;
    private final AuditoriaService auditoria;
    private final ControleAcesso acesso;

    public LocalService(LocalRepository locais, GeocodificacaoService geocodificacao, AuditoriaService auditoria,
                        ControleAcesso acesso) {
        this.locais = locais;
        this.geocodificacao = geocodificacao;
        this.auditoria = auditoria;
        this.acesso = acesso;
    }

    @Transactional(readOnly = true)
    public List<Local> listar() {
        return locais.findAllByOrderByNome();
    }

    @Transactional(readOnly = true)
    public List<Local> listarAtivos() {
        return locais.findByAtivoTrueOrderByNome();
    }

    @Transactional(readOnly = true)
    public Local buscar(Long id) {
        return locais.findById(id).orElseThrow(() -> new NaoEncontradoException("Ponto cadastrado não encontrado."));
    }

    @Transactional
    public Local criar(Dados dados) {
        acesso.exigirGestor();
        validar(dados);
        Local local = new Local(dados.nome().trim(), dados.endereco().trim(), dados.latitude(), dados.longitude());
        completarCoordenadas(local);
        locais.save(local);
        auditoria.criacao("Local", local.getId(), "Ponto cadastrado " + local.getNome() + " - " + local.getEndereco(),
                acesso.exigirUsuario().escopoGerenteId());
        return local;
    }

    @Transactional
    public Local atualizar(Long id, Dados dados) {
        acesso.exigirGestor();
        validar(dados);
        Local local = buscar(id);
        String descricao = "Ponto cadastrado " + local.getNome();
        Long escopo = acesso.exigirUsuario().escopoGerenteId();
        boolean mudouEndereco = !local.getEndereco().equals(dados.endereco().trim());
        BigDecimal latAntes = local.getLatitude();
        BigDecimal lonAntes = local.getLongitude();
        auditoria.alteracao("Local", id, descricao, "Nome", local.getNome(), dados.nome().trim(), null, escopo);
        auditoria.alteracao("Local", id, descricao, "Endereço", local.getEndereco(), dados.endereco().trim(), null, escopo);
        local.setNome(dados.nome().trim());
        local.setEndereco(dados.endereco().trim());
        local.setLatitude(dados.latitude());
        local.setLongitude(dados.longitude());
        if (mudouEndereco && dados.latitude() == null) {
            completarCoordenadas(local);
        }
        auditoria.alteracao("Local", id, descricao, "Latitude", latAntes, local.getLatitude(), null, escopo);
        auditoria.alteracao("Local", id, descricao, "Longitude", lonAntes, local.getLongitude(), null, escopo);
        return local;
    }

    /** Os pontos dos roteiros guardam uma cópia do endereço, então desativar não altera o histórico. */
    @Transactional
    public void alterarAtivo(Long id, boolean ativo) {
        acesso.exigirGestor();
        Local local = buscar(id);
        auditoria.alteracao("Local", id, "Ponto cadastrado " + local.getNome(), "Ativo",
                local.isAtivo() ? "Sim" : "Não", ativo ? "Sim" : "Não", null, acesso.exigirUsuario().escopoGerenteId());
        local.setAtivo(ativo);
    }

    private void completarCoordenadas(Local local) {
        if (local.getLatitude() == null || local.getLongitude() == null) {
            geocodificacao.buscar(local.getEndereco()).ifPresent(r -> {
                local.setLatitude(r.latitude());
                local.setLongitude(r.longitude());
            });
        }
    }

    private static void validar(Dados dados) {
        if (dados.nome() == null || dados.nome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do ponto.");
        }
        if (dados.endereco() == null || dados.endereco().isBlank()) {
            throw new RegraNegocioException("Informe o endereço.");
        }
        validarCoordenadas(dados.latitude(), dados.longitude());
    }

    public static void validarCoordenadas(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new RegraNegocioException("Informe latitude e longitude juntas (ou deixe as duas vazias para buscar pelo endereço).");
        }
        if (latitude != null && (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new RegraNegocioException("Coordenadas inválidas: a latitude vai de -90 a 90 e a longitude de -180 a 180.");
        }
    }
}
