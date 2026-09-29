package br.pucminas.pontomorto;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Teste de integração: sobe o sistema inteiro com o banco H2 (modo PostgreSQL), as migrações Flyway
 * e os dados de exemplo (roteiros A, B e C de ontem, roteiros de hoje e 2 meses de histórico).
 * Cada teste roda em uma transação desfeita no final, então um teste não interfere no outro.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public @interface TesteIntegracao {
}
