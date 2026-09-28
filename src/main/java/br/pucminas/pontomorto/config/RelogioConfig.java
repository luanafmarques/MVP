package br.pucminas.pontomorto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Relógio único do sistema, no fuso de Brasília. Nos testes ele pode ser trocado por um relógio fixo. */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio(PontoMortoProperties propriedades) {
        return Clock.system(ZoneId.of(propriedades.fusoHorario()));
    }
}
