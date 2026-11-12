package com.example.java_mind_agent.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.java_mind_agent.support.RepositoryIntegrationTest;

@RepositoryIntegrationTest
class SchemaMigrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Environment environment;

	/**
	 * Com ddl-auto=validate o Hibernate compara cada entidade com o schema criado pelo Flyway ao subir o contexto;
	 * se alguém trocar o valor, os testes de repositório deixam de proteger o mapeamento.
	 */
	@Test
	void validaOMapeamentoJpaContraOSchemaDoFlyway() {
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
	}

	@Test
	void aplicaAsMigrationsDoZeroSemFalhas() {
		Integer falhas = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success = false", Integer.class);
		List<String> versoes = jdbc.queryForList(
				"select version from flyway_schema_history where version is not null order by installed_rank",
				String.class);

		assertThat(falhas).isZero();
		assertThat(versoes).startsWith("1", "2", "3", "4");
	}

	@Test
	void ativaAExtensaoPgvector() {
		String versao = jdbc.queryForObject(
				"select extversion from pg_extension where extname = 'vector'", String.class);

		assertThat(versao).isNotBlank();
	}

	@Test
	void criaTodasAsTabelasEsperadas() {
		List<String> tabelas = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'", String.class);

		assertThat(tabelas).contains("app_setting", "conversation", "message", "attachment", "pdf_document",
				"pdf_job", "question", "answer", "corpus_source", "corpus_chunk");
	}

	@Test
	void criaOsIndicesDeBuscaDoCorpus() {
		List<String> indices = jdbc.queryForList(
				"select indexdef from pg_indexes where tablename = 'corpus_chunk'", String.class);

		assertThat(indices).anyMatch(def -> def.contains("USING hnsw") && def.contains("vector_cosine_ops"));
		assertThat(indices).anyMatch(def -> def.contains("USING gin") && def.contains("jsonb_path_ops"));
	}
}
