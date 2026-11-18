package com.example.java_mind_agent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.knowledge.version.JavaVersionRegistry;

/** As ferramentas de demonstração: dão fatos do registro de versões, sem inventar nada. */
class DebugToolsTest {

	private final JavaVersionRegistry registro = new JavaVersionRegistry();

	private final DebugTools ferramentas = new DebugTools(registro);

	@Test
	void sobeComoDuasFerramentasNoRegistro() {
		ToolRegistry tools = new ToolRegistry(List.of(ferramentas));

		assertThat(tools.names()).containsExactly("current_date_time", "java_release_info");
		assertThat(tools.find("java_release_info").orElseThrow().getToolDefinition().inputSchema())
			.contains("\"version\"");
	}

	@Test
	void releaseInfoDaOsFatosDoRegistro() {
		String texto = ferramentas.javaReleaseInfo(17);

		assertThat(texto).startsWith("Java 17 (2021), LTS")
			.contains("- recursos:")
			.contains("- removido:")
			.contains("- depreciado:");
	}

	@Test
	void versaoDesconhecidaDizQueNaoTemDados() {
		assertThat(ferramentas.javaReleaseInfo(99)).startsWith("Não tenho dados da versão 99.").contains("de 1 a 27");
	}

	@Test
	void dataEHoraVemEmIso() {
		assertThat(ferramentas.currentDateTime()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*");
	}
}
