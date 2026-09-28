package br.pucminas.pontomorto.config;

import java.math.BigDecimal;
import java.util.List;

/**
 * Endereços usados nos dados de exemplo (Belo Horizonte e Contagem/MG).
 * As coordenadas são aproximadas e servem só para demonstração.
 */
public final class EnderecosExemplo {

    private EnderecosExemplo() {
    }

    /**
     * @param lentidao multiplicador do tempo típico parado no endereço (ex.: centros de distribuição demoram mais)
     */
    public record Endereco(String texto, double latitude, double longitude, double lentidao) {
        public BigDecimal lat() {
            return BigDecimal.valueOf(latitude);
        }

        public BigDecimal lon() {
            return BigDecimal.valueOf(longitude);
        }
    }

    public static final Endereco BASE = new Endereco("Seg. Família (base) - Belo Horizonte/MG", -19.9290, -43.9710, 1);

    // Roteiro A do enunciado
    public static final Endereco RUA_PERU = new Endereco("Rua Peru, 55 - Sion, Belo Horizonte/MG", -19.9528, -43.9335, 1.1);
    public static final Endereco RUA_X = new Endereco("Rua X, 5 - Belo Horizonte/MG", -19.9410, -43.9470, 0.8);
    public static final Endereco JOAO_CESAR = new Endereco("Av. João César de Oliveira, 1000 - Contagem/MG", -19.9373, -44.0508, 2.6);

    // Roteiro B
    public static final Endereco RUA_BAHIA = new Endereco("Rua da Bahia, 1148 - Centro, Belo Horizonte/MG", -19.9227, -43.9377, 1.0);
    public static final Endereco AFONSO_PENA = new Endereco("Av. Afonso Pena, 1500 - Centro, Belo Horizonte/MG", -19.9265, -43.9344, 0.7);
    public static final Endereco AMAZONAS = new Endereco("Av. Amazonas, 7000 - Gameleira, Belo Horizonte/MG", -19.9450, -43.9860, 1.9);

    // Roteiro C
    public static final Endereco CRISTIANO_MACHADO = new Endereco("Av. Cristiano Machado, 4000 - União, Belo Horizonte/MG", -19.8830, -43.9300, 0.6);
    public static final Endereco PADRE_EUSTAQUIO = new Endereco("Rua Padre Eustáquio, 1800 - Padre Eustáquio, Belo Horizonte/MG", -19.9150, -43.9690, 0.9);
    public static final Endereco PEDRO_II = new Endereco("Av. Pedro II, 3000 - Padre Eustáquio, Belo Horizonte/MG", -19.9110, -43.9730, 2.0);

    /** Endereços sorteados no histórico gerado. */
    public static final List<Endereco> TODOS = List.of(
            RUA_PERU, RUA_X, JOAO_CESAR, RUA_BAHIA, AFONSO_PENA, AMAZONAS, CRISTIANO_MACHADO, PADRE_EUSTAQUIO, PEDRO_II,
            new Endereco("Rua Espírito Santo, 900 - Centro, Belo Horizonte/MG", -19.9208, -43.9420, 0.8),
            new Endereco("Av. Dom José Gaspar, 500 - Coração Eucarístico, Belo Horizonte/MG", -19.9227, -43.9925, 1.0),
            new Endereco("Av. do Contorno, 6000 - Savassi, Belo Horizonte/MG", -19.9380, -43.9350, 1.2),
            new Endereco("Rua Rio Grande do Norte, 1000 - Savassi, Belo Horizonte/MG", -19.9360, -43.9340, 0.7),
            new Endereco("Av. Raja Gabaglia, 2000 - Luxemburgo, Belo Horizonte/MG", -19.9530, -43.9500, 1.3),
            new Endereco("Av. Barão Homem de Melo, 3000 - Estoril, Belo Horizonte/MG", -19.9600, -43.9650, 0.9),
            new Endereco("Av. Silviano Brandão, 1500 - Horto, Belo Horizonte/MG", -19.9060, -43.9230, 0.8),
            new Endereco("Av. Antônio Carlos, 6627 - Pampulha, Belo Horizonte/MG", -19.8700, -43.9650, 1.5),
            new Endereco("Rua Itapecerica, 300 - Lagoinha, Belo Horizonte/MG", -19.9060, -43.9460, 0.7),
            new Endereco("Av. Prudente de Morais, 800 - Cidade Jardim, Belo Horizonte/MG", -19.9430, -43.9500, 0.9),
            new Endereco("Av. Babita Camargos, 1000 - Cidade Industrial, Contagem/MG", -19.9450, -44.0350, 2.2),
            new Endereco("Av. General David Sarnoff, 3000 - Cidade Industrial, Contagem/MG", -19.9600, -44.0160, 2.4));
}
