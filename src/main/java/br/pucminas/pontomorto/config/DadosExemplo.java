package br.pucminas.pontomorto.config;

import br.pucminas.pontomorto.config.EnderecosExemplo.Endereco;
import br.pucminas.pontomorto.dominio.Gerente;
import br.pucminas.pontomorto.dominio.Local;
import br.pucminas.pontomorto.dominio.Motorista;
import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.Perfil;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.StatusPedido;
import br.pucminas.pontomorto.dominio.StatusRoteiro;
import br.pucminas.pontomorto.dominio.TipoVeiculo;
import br.pucminas.pontomorto.dominio.Usuario;
import br.pucminas.pontomorto.dominio.Veiculo;
import br.pucminas.pontomorto.repositorio.GerenteRepository;
import br.pucminas.pontomorto.repositorio.LocalRepository;
import br.pucminas.pontomorto.repositorio.MotoristaRepository;
import br.pucminas.pontomorto.repositorio.PedidoRepository;
import br.pucminas.pontomorto.repositorio.RoteiroRepository;
import br.pucminas.pontomorto.repositorio.UsuarioRepository;
import br.pucminas.pontomorto.repositorio.VeiculoRepository;
import br.pucminas.pontomorto.servico.CalculoRoteiroService;
import br.pucminas.pontomorto.servico.DistanciaService;
import br.pucminas.pontomorto.servico.ParametroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

/**
 * Carrega os dados de exemplo quando o banco está vazio:
 * <ul>
 *   <li>1 administrador, 1 gerente e 3 motoristas (com veículos);</li>
 *   <li>os roteiros A, B e C do enunciado (ontem), com pedidos ligados aos pontos;</li>
 *   <li>roteiros planejados para hoje (para testar os botões "Cheguei" e "Saí") e pedidos pendentes;</li>
 *   <li>histórico de dias úteis dos últimos meses, para os gráficos de mês e período.</li>
 * </ul>
 */
@Component
public class DadosExemplo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DadosExemplo.class);

    public static final String SENHA_ADMIN = "admin123";
    public static final String SENHA_GERENTE = "gerente123";
    public static final String SENHA_MOTORISTA = "motorista123";

    private final PontoMortoProperties propriedades;
    private final UsuarioRepository usuarios;
    private final GerenteRepository gerentes;
    private final MotoristaRepository motoristas;
    private final VeiculoRepository veiculos;
    private final LocalRepository locais;
    private final PedidoRepository pedidos;
    private final RoteiroRepository roteiros;
    private final ParametroService parametros;
    private final CalculoRoteiroService calculo;
    private final DistanciaService distancia;
    private final GeradorHistorico gerador;
    private final PasswordEncoder senhas;
    private final Clock relogio;

    private int proximoPedido = 1;

    public DadosExemplo(PontoMortoProperties propriedades, UsuarioRepository usuarios, GerenteRepository gerentes,
                        MotoristaRepository motoristas, VeiculoRepository veiculos, LocalRepository locais,
                        PedidoRepository pedidos, RoteiroRepository roteiros, ParametroService parametros,
                        CalculoRoteiroService calculo, DistanciaService distancia, GeradorHistorico gerador,
                        PasswordEncoder senhas, Clock relogio) {
        this.propriedades = propriedades;
        this.usuarios = usuarios;
        this.gerentes = gerentes;
        this.motoristas = motoristas;
        this.veiculos = veiculos;
        this.locais = locais;
        this.pedidos = pedidos;
        this.roteiros = roteiros;
        this.parametros = parametros;
        this.calculo = calculo;
        this.distancia = distancia;
        this.gerador = gerador;
        this.senhas = senhas;
        this.relogio = relogio;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!propriedades.dadosExemplo().habilitado() || usuarios.count() > 0) {
            return;
        }
        long inicio = System.currentTimeMillis();
        LOG.info("Banco vazio: carregando os dados de exemplo do Ponto Morto...");
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate ontem = hoje.minusDays(1);
        LocalDateTime agora = LocalDateTime.now(relogio);
        Parametro parametro = parametros.obter();

        // ---------- Usuários ----------
        usuarios.save(new Usuario("admin@pontomorto.com.br", senhas.encode(SENHA_ADMIN), Perfil.ADMIN, agora));

        Gerente carla = new Gerente("Carla Mendes", "(31) 98888-1000", "gerente@pontomorto.com.br");
        carla.setUsuario(usuarios.save(new Usuario("gerente@pontomorto.com.br", senhas.encode(SENHA_GERENTE), Perfil.GERENTE, agora)));
        gerentes.save(carla);

        Motorista joao = motorista("João Silva", "(31) 97777-2001", "12345678909", "joao@pontomorto.com.br", carla,
                new Veiculo("ABC1D23", TipoVeiculo.MOTO, "Honda CG 160", new BigDecimal("38.0")), agora);
        Motorista maria = motorista("Maria Souza", "(31) 97777-2002", "98765432100", "maria@pontomorto.com.br", carla,
                new Veiculo("QWE4R56", TipoVeiculo.VAN, "Fiat Fiorino", new BigDecimal("11.0")), agora);
        Motorista pedro = motorista("Pedro Santos", "(31) 97777-2003", "45678912366", "pedro@pontomorto.com.br", carla,
                new Veiculo("RTY7U89", TipoVeiculo.CARRO, "Renault Kwid", new BigDecimal("14.5")), agora);

        // ---------- Pontos cadastrados (RF03) ----------
        Local base = locais.save(new Local("Seg. Família (base de partida)", EnderecosExemplo.BASE.texto(),
                EnderecosExemplo.BASE.lat(), EnderecosExemplo.BASE.lon()));
        locais.save(new Local("Centro de distribuição Contagem", EnderecosExemplo.JOAO_CESAR.texto(),
                EnderecosExemplo.JOAO_CESAR.lat(), EnderecosExemplo.JOAO_CESAR.lon()));

        // ---------- Roteiros A, B e C do enunciado (ontem) ----------
        roteiroDoEnunciado(joao, ontem, carla, base, LocalTime.of(8, 0), new BigDecimal("15230.0"),
                new Endereco[]{EnderecosExemplo.RUA_PERU, EnderecosExemplo.RUA_X, EnderecosExemplo.JOAO_CESAR},
                new int[]{25, 20, 30}, new int[]{15, 10, 50}, new int[]{2, 1, 1}, parametro);
        roteiroDoEnunciado(maria, ontem, carla, base, LocalTime.of(7, 55), new BigDecimal("48112.0"),
                new Endereco[]{EnderecosExemplo.RUA_BAHIA, EnderecosExemplo.AFONSO_PENA, EnderecosExemplo.AMAZONAS},
                new int[]{20, 10, 25}, new int[]{10, 5, 26}, new int[]{1, 1, 1}, parametro);
        roteiroDoEnunciado(pedro, ontem, carla, base, LocalTime.of(8, 10), new BigDecimal("30455.0"),
                new Endereco[]{EnderecosExemplo.CRISTIANO_MACHADO, EnderecosExemplo.PADRE_EUSTAQUIO, EnderecosExemplo.PEDRO_II},
                new int[]{25, 25, 10}, new int[]{5, 10, 30}, new int[]{1, 1, 1}, parametro);

        // ---------- Hoje: roteiros planejados + pedidos pendentes ----------
        roteiroPlanejado(joao, hoje, carla, base, parametro, agora,
                EnderecosExemplo.RUA_PERU, EnderecosExemplo.RUA_PERU, EnderecosExemplo.AFONSO_PENA, EnderecosExemplo.JOAO_CESAR);
        roteiroPlanejado(maria, hoje, carla, base, parametro, agora,
                EnderecosExemplo.RUA_BAHIA, EnderecosExemplo.AMAZONAS, EnderecosExemplo.PADRE_EUSTAQUIO);
        roteiroPlanejado(pedro, hoje, carla, base, parametro, agora,
                EnderecosExemplo.CRISTIANO_MACHADO, EnderecosExemplo.RUA_X, EnderecosExemplo.PEDRO_II);
        pedidoPendente(hoje, carla, EnderecosExemplo.TODOS.get(9), "Papelaria Bahia", agora);
        LocalDate amanha = hoje.plusDays(1);
        pedidoPendente(amanha, carla, EnderecosExemplo.RUA_PERU, "Farmácia Sion", agora);
        pedidoPendente(amanha, carla, EnderecosExemplo.RUA_PERU, "Padaria Sion", agora);
        pedidoPendente(amanha, carla, EnderecosExemplo.TODOS.get(11), "Loja Savassi Modas", agora);
        pedidoPendente(amanha, carla, EnderecosExemplo.JOAO_CESAR, "Atacado Contagem", agora);
        pedidoPendente(amanha, carla, EnderecosExemplo.TODOS.get(16), "Livraria Pampulha", agora);

        // ---------- Histórico para os gráficos de mês e período ----------
        int meses = Math.max(propriedades.dadosExemplo().mesesHistorico(), 0);
        int historico = 0;
        if (meses > 0) {
            historico = gerador.gerar(List.of(joao, maria, pedro), hoje.minusMonths(meses), ontem.minusDays(1),
                    parametro, new Random(2026));
        }
        LOG.info("Dados de exemplo carregados em {} ms: 3 roteiros do enunciado, 3 roteiros de hoje e {} roteiros "
                + "de histórico ({} meses).", System.currentTimeMillis() - inicio, historico, meses);
        LOG.info("Logins de exemplo: admin@pontomorto.com.br / {} · gerente@pontomorto.com.br / {} · "
                + "joao@pontomorto.com.br, maria@pontomorto.com.br, pedro@pontomorto.com.br / {}",
                SENHA_ADMIN, SENHA_GERENTE, SENHA_MOTORISTA);
    }

    private Motorista motorista(String nome, String telefone, String documento, String login, Gerente gerente,
                                Veiculo veiculo, LocalDateTime agora) {
        Motorista motorista = new Motorista(nome, telefone, documento);
        motorista.setUsuario(usuarios.save(new Usuario(login, senhas.encode(SENHA_MOTORISTA), Perfil.MOTORISTA, agora)));
        motorista.setGerente(gerente);
        motorista.setVeiculo(veiculos.save(veiculo));
        return motoristas.save(motorista);
    }

    /**
     * Roteiro concluído com os tempos exatos do enunciado. A partida não conta tempo parado (RN01);
     * cada ponto seguinte fica parado os minutos informados.
     */
    private void roteiroDoEnunciado(Motorista motorista, LocalDate dia, Gerente gerente, Local base, LocalTime saidaDaBase,
                                    BigDecimal kmInicial, Endereco[] enderecos, int[] deslocamentoMin, int[] paradoMin,
                                    int[] pedidosPorPonto, Parametro parametro) {
        Roteiro roteiro = new Roteiro(dia, motorista, dia.atTime(6, 30));
        Ponto partida = roteiro.adicionarPonto(Ponto.deLocal(base));
        LocalDateTime relogioDoDia = dia.atTime(saidaDaBase);
        partida.setChegada(relogioDoDia.minusMinutes(15));
        partida.setSaida(relogioDoDia);
        for (int i = 0; i < enderecos.length; i++) {
            Endereco e = enderecos[i];
            Ponto ponto = roteiro.adicionarPonto(new Ponto(e.texto(), e.lat(), e.lon()));
            relogioDoDia = relogioDoDia.plusMinutes(deslocamentoMin[i]);
            ponto.setChegada(relogioDoDia);
            relogioDoDia = relogioDoDia.plusMinutes(paradoMin[i]);
            ponto.setSaida(relogioDoDia);
            for (int p = 0; p < pedidosPorPonto[i]; p++) {
                Pedido pedido = novoPedido(dia, gerente, e, clienteDe(e, p), dia.minusDays(2).atTime(16, 0));
                pedido.associarAoPonto(ponto);
                pedido.setStatus(StatusPedido.ENTREGUE);
            }
        }
        distancia.estimarPara(roteiro);
        BigDecimal estimada = roteiro.getDistanciaEstimadaKm();
        BigDecimal real = estimada.multiply(new BigDecimal("1.12")).add(BigDecimal.ONE).setScale(1, RoundingMode.HALF_UP);
        roteiro.definirHodometro(kmInicial, kmInicial.add(real));
        roteiro.setStatus(StatusRoteiro.CONCLUIDO);
        calculo.recalcular(roteiro, parametro);
        roteiros.save(roteiro);
    }

    /** Roteiro de hoje, ainda sem horários: um ponto por endereço, mesmo com mais de um pedido. */
    private void roteiroPlanejado(Motorista motorista, LocalDate dia, Gerente gerente, Local base, Parametro parametro,
                                  LocalDateTime agora, Endereco... enderecos) {
        Roteiro roteiro = new Roteiro(dia, motorista, agora);
        roteiro.adicionarPonto(Ponto.deLocal(base));
        Ponto anterior = null;
        Endereco enderecoAnterior = null;
        int repeticao = 0;
        for (Endereco e : enderecos) {
            Ponto ponto;
            if (e == enderecoAnterior) {
                ponto = anterior;
                repeticao++;
            } else {
                ponto = roteiro.adicionarPonto(new Ponto(e.texto(), e.lat(), e.lon()));
                repeticao = 0;
            }
            novoPedido(dia, gerente, e, clienteDe(e, repeticao), agora).associarAoPonto(ponto);
            anterior = ponto;
            enderecoAnterior = e;
        }
        distancia.estimarPara(roteiro);
        calculo.recalcular(roteiro, parametro);
        roteiros.save(roteiro);
    }

    private void pedidoPendente(LocalDate dia, Gerente gerente, Endereco e, String cliente, LocalDateTime agora) {
        novoPedido(dia, gerente, e, cliente, agora);
    }

    private Pedido novoPedido(LocalDate dia, Gerente gerente, Endereco e, String cliente, LocalDateTime criadoEm) {
        Pedido pedido = new Pedido(String.format("PM-%d-%04d", dia.getYear(), proximoPedido++), cliente, e.texto(), dia, criadoEm);
        pedido.setLatitude(e.lat());
        pedido.setLongitude(e.lon());
        pedido.setGerente(gerente);
        return pedidos.save(pedido);
    }

    private static String clienteDe(Endereco e, int indice) {
        String[] nomes = {"Mercearia", "Farmácia", "Padaria", "Ótica", "Loja", "Restaurante"};
        String rua = e.texto().split(",")[0].replace("Av. ", "").replace("Rua ", "");
        return nomes[(Math.abs(rua.hashCode()) + indice) % nomes.length] + " " + rua;
    }
}
