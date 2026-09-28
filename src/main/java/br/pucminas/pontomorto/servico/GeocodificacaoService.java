package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.config.PontoMortoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RF03: busca latitude e longitude a partir do endereço, usando a API gratuita do Nominatim (OpenStreetMap).
 * Segue a política de uso do Nominatim: identificação no User-Agent, no máximo 1 consulta por segundo e cache.
 * O resultado pode ser ajustado manualmente na tela.
 */
@Service
public class GeocodificacaoService {

    private static final Logger LOG = LoggerFactory.getLogger(GeocodificacaoService.class);

    public record Resultado(BigDecimal latitude, BigDecimal longitude, String enderecoEncontrado) {
    }

    private final PontoMortoProperties.Geocodificacao config;
    private final RestClient cliente;
    private final Map<String, Optional<Resultado>> cache = new ConcurrentHashMap<>();
    private long ultimaChamada;

    public GeocodificacaoService(PontoMortoProperties propriedades, RestClient.Builder builder) {
        this.config = propriedades.geocodificacao();
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5000);
        fabrica.setReadTimeout(8000);
        this.cliente = builder.requestFactory(fabrica)
                .defaultHeader("User-Agent", config.userAgent())
                .defaultHeader("Accept-Language", "pt-BR")
                .build();
    }

    public boolean isHabilitado() {
        return config.habilitado();
    }

    public Optional<Resultado> buscar(String endereco) {
        if (!config.habilitado() || endereco == null || endereco.isBlank()) {
            return Optional.empty();
        }
        String chave = endereco.trim().toLowerCase();
        Optional<Resultado> emCache = cache.get(chave);
        if (emCache != null) {
            return emCache;
        }
        Optional<Resultado> resultado = consultar(endereco.trim());
        cache.put(chave, resultado);
        return resultado;
    }

    private Optional<Resultado> consultar(String endereco) {
        try {
            respeitarLimiteDeUso();
            String uri = UriComponentsBuilder.fromUriString(config.url())
                    .queryParam("q", endereco)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .queryParam("countrycodes", "br")
                    .encode()
                    .toUriString();
            JsonNode lista = cliente.get().uri(java.net.URI.create(uri)).retrieve().body(JsonNode.class);
            if (lista != null && lista.isArray() && !lista.isEmpty()) {
                JsonNode primeiro = lista.get(0);
                return Optional.of(new Resultado(
                        new BigDecimal(primeiro.path("lat").asText()).setScale(6, RoundingMode.HALF_UP),
                        new BigDecimal(primeiro.path("lon").asText()).setScale(6, RoundingMode.HALF_UP),
                        primeiro.path("display_name").asText(null)));
            }
        } catch (Exception e) {
            LOG.warn("Não foi possível geocodificar \"{}\": {}", endereco, e.getMessage());
        }
        return Optional.empty();
    }

    private synchronized void respeitarLimiteDeUso() throws InterruptedException {
        long espera = 1100 - (System.currentTimeMillis() - ultimaChamada);
        if (espera > 0) {
            Thread.sleep(espera);
        }
        ultimaChamada = System.currentTimeMillis();
    }
}
