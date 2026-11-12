package com.example.java_mind_agent.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Teste de repositório contra um Postgres + pgvector real (Testcontainers).
 * Roda as migrations do Flyway e valida o mapeamento JPA contra o schema (ddl-auto=validate).
 * Exige o Docker em execução. Todas as classes que usam esta anotação compartilham o mesmo contexto/container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public @interface RepositoryIntegrationTest {
}
