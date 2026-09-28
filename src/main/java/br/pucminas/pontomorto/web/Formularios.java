package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Gerente;
import br.pucminas.pontomorto.dominio.Local;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.TipoVeiculo;
import br.pucminas.pontomorto.servico.GerenteService;
import br.pucminas.pontomorto.servico.LocalService;
import br.pucminas.pontomorto.servico.MotoristaService;
import br.pucminas.pontomorto.servico.ParametroService;
import br.pucminas.pontomorto.servico.PedidoService;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Objetos ligados aos formulários das telas (Thymeleaf th:object). */
public final class Formularios {

    private Formularios() {
    }

    public static class PedidoForm {
        private Long id;
        private String numero;
        private String cliente;
        private String enderecoEntrega;
        private BigDecimal latitude;
        private BigDecimal longitude;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dataPrevista;
        private String observacao;
        private Long gerenteId;

        public static PedidoForm de(Pedido p) {
            PedidoForm f = new PedidoForm();
            f.id = p.getId();
            f.numero = p.getNumero();
            f.cliente = p.getCliente();
            f.enderecoEntrega = p.getEnderecoEntrega();
            f.latitude = p.getLatitude();
            f.longitude = p.getLongitude();
            f.dataPrevista = p.getDataPrevista();
            f.observacao = p.getObservacao();
            f.gerenteId = p.getGerente() == null ? null : p.getGerente().getId();
            return f;
        }

        public PedidoService.Dados dados() {
            return new PedidoService.Dados(numero, cliente, enderecoEntrega, latitude, longitude, dataPrevista, observacao, gerenteId);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
        public String getCliente() { return cliente; }
        public void setCliente(String cliente) { this.cliente = cliente; }
        public String getEnderecoEntrega() { return enderecoEntrega; }
        public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
        public LocalDate getDataPrevista() { return dataPrevista; }
        public void setDataPrevista(LocalDate dataPrevista) { this.dataPrevista = dataPrevista; }
        public String getObservacao() { return observacao; }
        public void setObservacao(String observacao) { this.observacao = observacao; }
        public Long getGerenteId() { return gerenteId; }
        public void setGerenteId(Long gerenteId) { this.gerenteId = gerenteId; }
    }

    public static class MotoristaForm {
        private Long id;
        private String nome;
        private String telefone;
        private String documento;
        private Long gerenteId;
        private String placa;
        private TipoVeiculo tipoVeiculo = TipoVeiculo.MOTO;
        private String modeloVeiculo;
        private BigDecimal rendimentoKmLitro;
        private String login;
        private String senha;

        public static MotoristaForm de(Motorista m) {
            MotoristaForm f = new MotoristaForm();
            f.id = m.getId();
            f.nome = m.getNome();
            f.telefone = m.getTelefone();
            f.documento = m.getDocumento();
            f.gerenteId = m.getGerente() == null ? null : m.getGerente().getId();
            if (m.getVeiculo() != null) {
                f.placa = m.getVeiculo().getPlaca();
                f.tipoVeiculo = m.getVeiculo().getTipo();
                f.modeloVeiculo = m.getVeiculo().getModelo();
                f.rendimentoKmLitro = m.getVeiculo().getRendimentoKmLitro();
            }
            f.login = m.getUsuario() == null ? null : m.getUsuario().getLogin();
            return f;
        }

        public MotoristaService.Dados dados() {
            return new MotoristaService.Dados(nome, telefone, documento, gerenteId, placa, tipoVeiculo, modeloVeiculo,
                    rendimentoKmLitro, login, senha);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getTelefone() { return telefone; }
        public void setTelefone(String telefone) { this.telefone = telefone; }
        public String getDocumento() { return documento; }
        public void setDocumento(String documento) { this.documento = documento; }
        public Long getGerenteId() { return gerenteId; }
        public void setGerenteId(Long gerenteId) { this.gerenteId = gerenteId; }
        public String getPlaca() { return placa; }
        public void setPlaca(String placa) { this.placa = placa; }
        public TipoVeiculo getTipoVeiculo() { return tipoVeiculo; }
        public void setTipoVeiculo(TipoVeiculo tipoVeiculo) { this.tipoVeiculo = tipoVeiculo; }
        public String getModeloVeiculo() { return modeloVeiculo; }
        public void setModeloVeiculo(String modeloVeiculo) { this.modeloVeiculo = modeloVeiculo; }
        public BigDecimal getRendimentoKmLitro() { return rendimentoKmLitro; }
        public void setRendimentoKmLitro(BigDecimal rendimentoKmLitro) { this.rendimentoKmLitro = rendimentoKmLitro; }
        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
        public String getSenha() { return senha; }
        public void setSenha(String senha) { this.senha = senha; }
    }

    public static class GerenteForm {
        private Long id;
        private String nome;
        private String telefone;
        private String email;
        private String senha;
        private List<Long> equipeIds = new ArrayList<>();

        public static GerenteForm de(Gerente g) {
            GerenteForm f = new GerenteForm();
            f.id = g.getId();
            f.nome = g.getNome();
            f.telefone = g.getTelefone();
            f.email = g.getEmail();
            f.equipeIds = new ArrayList<>(g.getEquipe().stream().map(Motorista::getId).toList());
            return f;
        }

        public GerenteService.Dados dados() {
            return new GerenteService.Dados(nome, telefone, email, senha, equipeIds);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getTelefone() { return telefone; }
        public void setTelefone(String telefone) { this.telefone = telefone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSenha() { return senha; }
        public void setSenha(String senha) { this.senha = senha; }
        public List<Long> getEquipeIds() { return equipeIds; }
        public void setEquipeIds(List<Long> equipeIds) { this.equipeIds = equipeIds == null ? new ArrayList<>() : equipeIds; }
    }

    public static class LocalForm {
        private Long id;
        private String nome;
        private String endereco;
        private BigDecimal latitude;
        private BigDecimal longitude;

        public static LocalForm de(Local l) {
            LocalForm f = new LocalForm();
            f.id = l.getId();
            f.nome = l.getNome();
            f.endereco = l.getEndereco();
            f.latitude = l.getLatitude();
            f.longitude = l.getLongitude();
            return f;
        }

        public LocalService.Dados dados() {
            return new LocalService.Dados(nome, endereco, latitude, longitude);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getEndereco() { return endereco; }
        public void setEndereco(String endereco) { this.endereco = endereco; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    }

    public static class ParametroForm {
        private BigDecimal precoCombustivel;
        private BigDecimal kmLitroPadrao;
        private BigDecimal custoPorKm;
        private BigDecimal jornadaHoras;
        private int ignorarParadaMenorQueMin;
        private int tempoMaximoParadaMin;
        private boolean recalcularConcluidos;

        public static ParametroForm de(Parametro p) {
            ParametroForm f = new ParametroForm();
            f.precoCombustivel = p.getPrecoCombustivel();
            f.kmLitroPadrao = p.getKmLitroPadrao();
            f.custoPorKm = p.getCustoPorKm();
            f.jornadaHoras = p.getJornadaHoras();
            f.ignorarParadaMenorQueMin = p.getIgnorarParadaMenorQueMin();
            f.tempoMaximoParadaMin = p.getTempoMaximoParadaMin();
            return f;
        }

        public ParametroService.Alteracao alteracao() {
            return new ParametroService.Alteracao(precoCombustivel, kmLitroPadrao, custoPorKm, jornadaHoras,
                    ignorarParadaMenorQueMin, tempoMaximoParadaMin);
        }

        public BigDecimal getPrecoCombustivel() { return precoCombustivel; }
        public void setPrecoCombustivel(BigDecimal precoCombustivel) { this.precoCombustivel = precoCombustivel; }
        public BigDecimal getKmLitroPadrao() { return kmLitroPadrao; }
        public void setKmLitroPadrao(BigDecimal kmLitroPadrao) { this.kmLitroPadrao = kmLitroPadrao; }
        public BigDecimal getCustoPorKm() { return custoPorKm; }
        public void setCustoPorKm(BigDecimal custoPorKm) { this.custoPorKm = custoPorKm; }
        public BigDecimal getJornadaHoras() { return jornadaHoras; }
        public void setJornadaHoras(BigDecimal jornadaHoras) { this.jornadaHoras = jornadaHoras; }
        public int getIgnorarParadaMenorQueMin() { return ignorarParadaMenorQueMin; }
        public void setIgnorarParadaMenorQueMin(int v) { this.ignorarParadaMenorQueMin = v; }
        public int getTempoMaximoParadaMin() { return tempoMaximoParadaMin; }
        public void setTempoMaximoParadaMin(int v) { this.tempoMaximoParadaMin = v; }
        public boolean isRecalcularConcluidos() { return recalcularConcluidos; }
        public void setRecalcularConcluidos(boolean recalcularConcluidos) { this.recalcularConcluidos = recalcularConcluidos; }
    }
}
