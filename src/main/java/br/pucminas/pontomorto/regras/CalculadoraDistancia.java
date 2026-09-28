package br.pucminas.pontomorto.regras;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Distâncias do roteiro.
 * <ul>
 *   <li>Estimada: soma pelas ruas (OSRM) ou, sem resposta da API, linha reta (Haversine) marcada como aproximada.</li>
 *   <li>Real: km final menos km inicial do hodômetro.</li>
 *   <li>O custo usa a distância real quando existir e a estimada enquanto isso.</li>
 * </ul>
 */
public final class CalculadoraDistancia {

    private static final double RAIO_TERRA_KM = 6371.0088;

    private CalculadoraDistancia() {
    }

    public record Coordenada(double latitude, double longitude) {
    }

    /** Distância em linha reta entre dois pontos da Terra (fórmula de Haversine), em km. */
    public static double haversineKm(Coordenada a, Coordenada b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Soma da linha reta de cada ponto até o seguinte, na ordem do roteiro. */
    public static BigDecimal linhaRetaKm(List<Coordenada> pontosEmOrdem) {
        double total = 0;
        for (int i = 1; i < pontosEmOrdem.size(); i++) {
            total += haversineKm(pontosEmOrdem.get(i - 1), pontosEmOrdem.get(i));
        }
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    /** Distância real = km final menos km inicial. O km final precisa ser maior que o inicial. */
    public static BigDecimal distanciaReal(BigDecimal kmInicial, BigDecimal kmFinal) {
        if (kmInicial == null || kmFinal == null) {
            return null;
        }
        validarHodometro(kmInicial, kmFinal);
        return kmFinal.subtract(kmInicial).setScale(1, RoundingMode.HALF_UP);
    }

    public static void validarHodometro(BigDecimal kmInicial, BigDecimal kmFinal) {
        if (kmInicial != null && kmInicial.signum() < 0) {
            throw new RegraNegocioException("O km do hodômetro não pode ser negativo.");
        }
        if (kmInicial != null && kmFinal != null && kmFinal.compareTo(kmInicial) <= 0) {
            throw new RegraNegocioException("O km final precisa ser maior que o km inicial.");
        }
    }

    /** Distância usada no custo: a real, se já existir; senão, a estimada. */
    public static BigDecimal distanciaParaCusto(BigDecimal distanciaReal, BigDecimal distanciaEstimada) {
        if (distanciaReal != null) {
            return distanciaReal;
        }
        return distanciaEstimada != null ? distanciaEstimada : BigDecimal.ZERO;
    }

    /** Diferença real menos estimada (positiva quando o motorista rodou mais do que o previsto). */
    public static BigDecimal diferenca(BigDecimal distanciaReal, BigDecimal distanciaEstimada) {
        if (distanciaReal == null || distanciaEstimada == null) {
            return null;
        }
        return distanciaReal.subtract(distanciaEstimada).setScale(1, RoundingMode.HALF_UP);
    }
}
