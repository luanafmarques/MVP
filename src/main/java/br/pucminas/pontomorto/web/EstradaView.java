package br.pucminas.pontomorto.web;

import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dados para desenhar o roteiro como uma estrada horizontal, com os pontos em ordem.
 * A ÁREA de cada círculo é proporcional ao tempo parado (o diâmetro cresce com a raiz quadrada),
 * para que uma parada de 50 min pareça 5 vezes maior que uma de 10 min, e não 25.
 */
public record EstradaView(Long roteiroId, String motorista, String titulo, List<Marco> marcos, long totalMin) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DIAMETRO_MIN = 16;
    private static final int DIAMETRO_MAX = 68;
    private static final int DIAMETRO_PARTIDA = 22;
    /** Escala mínima: 60 min enchem o círculo máximo, mesmo se todas as paradas forem curtas. */
    private static final long ESCALA_MINIMA_MIN = 60;

    /** @param tipo partida, parada, no-local (chegou e não saiu), pendente (ainda não visitado) */
    public record Marco(int ordem, String endereco, String enderecoCurto, Long minutos, int diametro, String tipo,
                        String chegada, String saida, String pedidos, boolean acimaDoMaximo, boolean atual) {
    }

    public static EstradaView de(Roteiro roteiro) {
        return de(roteiro, maiorParadaMin(roteiro));
    }

    /** Usa a mesma escala para vários roteiros na mesma tela, para que os tamanhos sejam comparáveis. */
    public static EstradaView de(Roteiro roteiro, long escalaMin) {
        long escala = Math.max(escalaMin, ESCALA_MINIMA_MIN);
        Long atualId = roteiro.getPontoAtual().map(Ponto::getId).orElse(null);
        List<Marco> marcos = roteiro.getPontos().stream().map(p -> {
            String tipo;
            int diametro;
            if (p.isPartida()) {
                tipo = "partida";
                diametro = DIAMETRO_PARTIDA;
            } else if (p.getSaida() != null) {
                tipo = "parada";
                long min = p.getTempoParadoMin() == null ? 0 : p.getTempoParadoMin();
                diametro = (int) Math.round(Math.max(DIAMETRO_MIN, DIAMETRO_MAX * Math.sqrt((double) min / escala)));
            } else if (p.getChegada() != null) {
                tipo = "no-local";
                diametro = DIAMETRO_MIN + 6;
            } else {
                tipo = "pendente";
                diametro = DIAMETRO_MIN;
            }
            String pedidos = p.getPedidos().stream().map(Pedido::getNumero).collect(Collectors.joining(", "));
            return new Marco(p.getOrdem(), p.getEndereco(), curto(p.getEndereco()),
                    p.isPartida() ? null : p.getTempoParadoMin(), Math.min(diametro, DIAMETRO_MAX), tipo,
                    p.getChegada() == null ? null : p.getChegada().format(HORA),
                    p.getSaida() == null ? null : p.getSaida().format(HORA),
                    pedidos, p.isAcimaDoMaximo(), p.getId() != null && p.getId().equals(atualId));
        }).toList();
        String titulo = "Roteiro de " + roteiro.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return new EstradaView(roteiro.getId(), roteiro.getMotorista().getNome(), titulo, marcos,
                roteiro.getTempoTotalParadoMin());
    }

    public static long maiorParadaMin(Roteiro roteiro) {
        return roteiro.getPontos().stream()
                .filter(p -> !p.isPartida() && p.getTempoParadoMin() != null)
                .mapToLong(Ponto::getTempoParadoMin)
                .max().orElse(0);
    }

    /** "Rua Peru, 55 - Sion, Belo Horizonte/MG" vira "Rua Peru, 55". */
    static String curto(String endereco) {
        int corte = endereco.indexOf(" - ");
        return corte > 0 ? endereco.substring(0, corte) : endereco;
    }

    public String descricaoAcessivel() {
        StringBuilder sb = new StringBuilder("Trajeto de ").append(motorista).append(": ");
        for (Marco m : marcos) {
            sb.append(m.ordem()).append(" ").append(m.enderecoCurto());
            if ("partida".equals(m.tipo())) {
                sb.append(" (partida)");
            } else if (m.minutos() != null) {
                sb.append(" (").append(m.minutos()).append(" min parado)");
            }
            sb.append("; ");
        }
        return sb.append("total parado ").append(totalMin).append(" min.").toString();
    }
}
