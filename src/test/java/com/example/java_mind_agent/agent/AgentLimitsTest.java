package com.example.java_mind_agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Os limites validam o que recebem, e {@code app.agent.limits} tem padrões que funcionam sem configurar nada. */
class AgentLimitsTest {

	private static final Duration UM_MINUTO = Duration.ofMinutes(1);

	@Test
	void valoresInvalidosSaoRecusados() {
		assertThatThrownBy(() -> new AgentLimits(0, 1, 1, UM_MINUTO, UM_MINUTO, 1)).hasMessageContaining("maxSteps");
		assertThatThrownBy(() -> new AgentLimits(1, -1, 1, UM_MINUTO, UM_MINUTO, 1)).hasMessageContaining("maxToolCalls");
		assertThatThrownBy(() -> new AgentLimits(1, 1, 0, UM_MINUTO, UM_MINUTO, 1)).hasMessageContaining("maxTokens");
		assertThatThrownBy(() -> new AgentLimits(1, 1, 1, Duration.ZERO, UM_MINUTO, 1)).hasMessageContaining("maxDuration");
		assertThatThrownBy(() -> new AgentLimits(1, 1, 1, UM_MINUTO, Duration.ofSeconds(-1), 1)).hasMessageContaining("toolTimeout");
		assertThatThrownBy(() -> new AgentLimits(1, 1, 1, UM_MINUTO, UM_MINUTO, 0)).hasMessageContaining("maxToolOutputChars");
	}

	@Test
	void semConfigurarNadaValemOsPadroes() {
		AgentProperties propriedades = new Binder(new MapConfigurationPropertySource(Map.of()))
			.bindOrCreate("app.agent", AgentProperties.class);

		assertThat(propriedades.limits()).isEqualTo(
				new AgentLimits(6, 10, 4096, Duration.ofMinutes(15), Duration.ofSeconds(60), 8000));
	}

	@Test
	void aConfiguracaoSobrescreveOPadraoCampoACampo() {
		AgentProperties propriedades = new Binder(new MapConfigurationPropertySource(Map.of(
				"app.agent.limits.max-steps", "3", "app.agent.limits.max-duration", "90s",
				"app.agent.limits.tool-timeout", "5s")))
			.bindOrCreate("app.agent", AgentProperties.class);

		assertThat(propriedades.limits()).isEqualTo(
				new AgentLimits(3, 10, 4096, Duration.ofSeconds(90), Duration.ofSeconds(5), 8000));
	}
}
