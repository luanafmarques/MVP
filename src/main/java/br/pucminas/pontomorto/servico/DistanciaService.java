package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.config.PontoMortoProperties;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.regras.CalculadoraDistancia;
import br.pucminas.pontomorto.regras.CalculadoraDistancia.Coordenada;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Distância estimada do roteiro: soma pelas ruas entre cada ponto e o seguinte, na ordem definida,
 * usando a API gratuita do OSRM. Se a API não responder, usa a linha reta (Haversine) e marca como aproximada.
 */
@Service
public class DistanciaService {

    private static final Logger LOG = LoggerFactory.getLogger(DistanciaService.class);

    public record Estimativa(BigDecimal km, boolean aproximada) {
    }

    private final PontoMortoProperties.Osrm config;
    private final RestClient cliente;
    private long ultimaChamada;

    public DistanciaService(PontoMortoProperties propriedades, RestClient.Builder builder) {
        this.config = propriedades.osrm();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(config.timeoutMs());
        fabrica.setReadTimeout(config.timeoutMs());
        this.cliente = builder.requestFactory(fabrica)
                .defaultHeader("User-Agent", "PontoMorto/1.0 (trabalho academico PUC Minas)")
                .build();
    }

    /** Recalcula e grava no roteiro a distância estimada. */
    public void estimarPara(Roteiro roteiro) {
        Estimativa estimativa = estimar(roteiro.getPontos());
        roteiro.definirDistanciaEstimada(estimativa.km(), estimativa.aproximada());
    }

    public Estimativa estimar(List<Ponto> pontosEmOrdem) {
        List<Coordenada> coordenadas = pontosEmOrdem.stream()
                .filter(Ponto::temCoordenadas)
                .map(p -> new Coordenada(p.getLatitude().doubleValue(), p.getLongitude().doubleValue()))
                .toList();
        boolean faltamCoordenadas = coordenadas.size() < pontosEmOrdem.size();
        if (coordenadas.size() < 2) {
            return new Estimativa(BigDecimal.ZERO.setScale(2), faltamCoordenadas);
        }
        if (config.habilitado()) {
            BigDecimal pelasRuas = consultarOsrm(coordenadas);
            if (pelasRuas != null) {
                return new Estimativa(pelasRuas, faltamCoordenadas);
            }
        }
        return new Estimativa(CalculadoraDistancia.linhaRetaKm(coordenadas), true);
    }

    /** Uma única chamada com todos os pontos: o OSRM devolve a distância total da rota em metros. */
    private BigDecimal consultarOsrm(List<Coordenada> coordenadas) {
        String caminho = coordenadas.stream()
                .map(c -> String.format(Locale.ROOT, "%.6f,%.6f", c.longitude(), c.latitude()))
                .collect(Collectors.joining(";"));
        try {
            respeitarLimiteDeUso();
            JsonNode resposta = cliente.get()
                    .uri(config.url() + "/" + caminho + "?overview=false")
                    .retrieve()
                    .body(JsonNode.class);
            if (resposta != null && "Ok".equals(resposta.path("code").asText())) {
                double metros = resposta.path("routes").path(0).path("distance").asDouble(-1);
                if (metros >= 0) {
                    return BigDecimal.valueOf(metros / 1000.0).setScale(2, RoundingMode.HALF_UP);
                }
            }
            LOG.warn("OSRM respondeu sem rota; usando linha reta. Resposta: {}", resposta);
        } catch (Exception e) {
            LOG.warn("OSRM indisponível ({}); usando linha reta (Haversine).", e.getMessage());
        }
        return null;
    }

    /** O servidor público do OSRM pede no máximo 1 requisição por segundo. */
    private synchronized void respeitarLimiteDeUso() throws InterruptedException {
        long espera = 1000 - (System.currentTimeMillis() - ultimaChamada);
        if (espera > 0) {
            Thread.sleep(espera);
        }
        ultimaChamada = System.currentTimeMillis();
    }
}
