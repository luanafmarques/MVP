package br.pucminas.pontomorto;

import org.springframework.boot.SpringApplication;

/**
 * Sobe o sistema com o banco H2 em memória, só para demonstração rápida sem PostgreSQL
 * (os dados somem ao parar). O banco oficial é o PostgreSQL: veja o README.
 */
public class PontoMortoComH2 {

    public static void main(String[] args) {
        SpringApplication.from(PontoMortoApplication::main).withAdditionalProfiles("h2").run(args);
    }
}
