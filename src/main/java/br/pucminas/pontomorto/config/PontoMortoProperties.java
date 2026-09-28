package br.pucminas.pontomorto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações próprias do sistema (bloco "pontomorto" do application.yml). */
@ConfigurationProperties(prefix = "pontomorto")
public record PontoMortoProperties(String fusoHorario, DadosExemplo dadosExemplo,
                                   Geocodificacao geocodificacao, Osrm osrm) {

    public record DadosExemplo(boolean habilitado, int mesesHistorico) {
    }

    public record Geocodificacao(boolean habilitado, String url, String userAgent) {
    }

    public record Osrm(boolean habilitado, String url, int timeoutMs) {
    }
}
