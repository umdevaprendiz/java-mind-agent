package com.example.java_mind_agent.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres descartável com pgvector para testes de integração.
 * Mantenha a imagem alinhada com a do compose.yaml (mesma versão do pgvector e do Postgres).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String PGVECTOR_IMAGE = "pgvector/pgvector:0.8.7-pg17";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(PGVECTOR_IMAGE).asCompatibleSubstituteFor("postgres"));
	}
}
