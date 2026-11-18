package com.example.java_mind_agent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.agent.FerramentasDeTeste;

/** O registro encontra as ferramentas declaradas com {@code @Tool} e recusa nomes repetidos. */
class ToolRegistryTest {

	private final FerramentasDeTeste conjunto = new FerramentasDeTeste();

	@Test
	void encontraAsFerramentasDeTodosOsConjuntos() {
		ToolRegistry registro = new ToolRegistry(List.of(conjunto));

		assertThat(registro.names()).containsExactly("eco", "grande", "lenta", "quebra", "vazia");
		assertThat(registro.callbacks()).hasSize(5);
		assertThat(registro.isEmpty()).isFalse();
	}

	@Test
	void entregaADefinicaoQueOModeloLeComDescricaoEParametros() {
		ToolCallback eco = new ToolRegistry(List.of(conjunto)).find("eco").orElseThrow();

		assertThat(eco.getToolDefinition().description()).isEqualTo("Repete o texto recebido, em maiúsculas");
		assertThat(eco.getToolDefinition().inputSchema()).contains("\"texto\"").contains("o texto a repetir");
	}

	@Test
	void semNenhumConjuntoEstaVazio() {
		ToolRegistry registro = new ToolRegistry(List.of());

		assertThat(registro.isEmpty()).isTrue();
		assertThat(registro.callbacks()).isEmpty();
		assertThat(registro.find("eco")).isEmpty();
	}

	@Test
	void nomeRepetidoImpedeASubida() {
		assertThatThrownBy(() -> new ToolRegistry(List.of(conjunto, new FerramentasDeTeste())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("declarada duas vezes")
			.hasMessageContaining(FerramentasDeTeste.class.getName());
	}

	@Test
	void aFasePerguntaAoConjuntoDono() {
		ToolRegistry registro = new ToolRegistry(List.of(conjunto));

		assertThat(registro.phaseOf("eco")).isEqualTo(Phase.EXECUTING_CODE);
		assertThat(registro.phaseOf("grande")).isEqualTo(Phase.GENERATING);
		assertThat(registro.phaseOf("nao_existe")).isEqualTo(Phase.GENERATING);
	}
}
