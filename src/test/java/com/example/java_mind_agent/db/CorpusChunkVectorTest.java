package com.example.java_mind_agent.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.java_mind_agent.support.RepositoryIntegrationTest;

/** Garante que a tabela corpus_chunk funciona como o RAG vai usá-la: vetor, similaridade e filtro de versão. */
@RepositoryIntegrationTest
class CorpusChunkVectorTest {

	/** Dimensão do bge-m3; precisa bater com vector(1024) em V4__corpus_de_documentacao.sql. */
	private static final int DIMENSAO = 1024;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void devolveOTrechoMaisProximoPorSimilaridadeDeCosseno() {
		inserir("record em Java 16", "{}", vetorComPesoEm(0));
		inserir("virtual threads em Java 21", "{}", vetorComPesoEm(1));

		List<String> maisProximos = jdbc.queryForList(
				"select content from corpus_chunk order by embedding <=> ?::vector limit 1",
				String.class, vetorComPesoEm(1));

		assertThat(maisProximos).containsExactly("virtual threads em Java 21");
	}

	@Test
	void filtraPorFaixaDeVersaoNosMetadados() {
		inserir("recurso do Java 8", "{\"java_version_min\": 8}", vetorComPesoEm(0));
		inserir("recurso do Java 21", "{\"java_version_min\": 21}", vetorComPesoEm(1));

		List<String> disponiveisNoJava17 = jdbc.queryForList(
				"select content from corpus_chunk where metadata @@ '$.java_version_min <= 17'::jsonpath",
				String.class);
		List<String> exatamenteJava21 = jdbc.queryForList(
				"select content from corpus_chunk where metadata @> ?::jsonb order by content",
				String.class, "{\"java_version_min\": 21}");

		assertThat(disponiveisNoJava17).containsExactly("recurso do Java 8");
		assertThat(exatamenteJava21).containsExactly("recurso do Java 21");
	}

	@Test
	void rejeitaVetorComDimensaoDiferenteDaDoModeloDeEmbedding() {
		assertThatThrownBy(() -> inserir("vetor curto", "{}", "[1,2,3]"))
				.isInstanceOf(DataAccessException.class);
	}

	private void inserir(String conteudo, String metadataJson, String vetor) {
		jdbc.update("insert into corpus_chunk (content, metadata, embedding) values (?, ?::jsonb, ?::vector)",
				conteudo, metadataJson, vetor);
	}

	/** Vetor de 1024 posições com 1 na posição indicada e 0 nas demais. */
	private static String vetorComPesoEm(int posicao) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < DIMENSAO; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(i == posicao ? '1' : '0');
		}
		return sb.append(']').toString();
	}
}
