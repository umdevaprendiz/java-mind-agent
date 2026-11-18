package com.example.java_mind_agent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import com.example.java_mind_agent.agent.AgentTexts;
import com.example.java_mind_agent.agent.FerramentasDeTeste;
import com.example.java_mind_agent.settings.Language;

import tools.jackson.databind.json.JsonMapper;

/** Executar uma ferramenta nunca falha: tudo que dá errado vira um resultado que o modelo lê. */
class ToolExecutorTest {

	private static final Duration TEMPO = Duration.ofSeconds(5);

	private final FerramentasDeTeste conjunto = new FerramentasDeTeste();

	private final ToolRegistry registro = new ToolRegistry(List.of(conjunto));

	private final ToolExecutor executor = new ToolExecutor(JsonMapper.builder().build());

	private final AgentTexts pt = AgentTexts.of(Language.PT_BR);

	@Test
	void devolveASaidaSemAsAspasDoJson() {
		ToolOutcome r = executa("eco", "{\"texto\":\"oi\"}", TEMPO, 1000);

		assertThat(r.ok()).isTrue();
		assertThat(r.output()).isEqualTo("OI");
		assertThat(r.summary()).isEqualTo("OI");
		assertThat(r.durationMs()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void oResumoEAPrimeiraLinhaComTextoEFicaCurto() {
		ToolOutcome multilinha = executa("eco", "{\"texto\":\"\\n  primeira\\nsegunda\"}", TEMPO, 1000);
		ToolOutcome longa = executa("grande", "{}", TEMPO, 1000);

		assertThat(multilinha.output()).contains("\n");
		assertThat(multilinha.summary()).isEqualTo("PRIMEIRA");
		assertThat(longa.summary()).hasSize(120).endsWith("…");
	}

	@Test
	void cortaASaidaNoLimiteEAvisa() {
		ToolOutcome r = executa("grande", "{}", TEMPO, 30);

		assertThat(r.output()).startsWith("x".repeat(30)).contains("saída cortada em 30 caracteres");
		assertThat(r.output()).doesNotContain("x".repeat(31));
	}

	@Test
	void ferramentaInexistenteListaAsQueExistem() {
		ToolOutcome r = executor.execute(null, "voa", registro.names(), "{}", TEMPO, 1000, pt).block();

		assertThat(r.ok()).isFalse();
		assertThat(r.output()).contains("A ferramenta 'voa' não existe").contains("eco");
		assertThat(r.durationMs()).isZero();
	}

	@Test
	void excecaoDaFerramentaViraErroComAMensagemDela() {
		ToolOutcome r = executa("quebra", "{}", TEMPO, 1000);

		assertThat(r.ok()).isFalse();
		assertThat(r.output()).isEqualTo("A ferramenta falhou: explodiu de propósito");
	}

	@Test
	void argumentosInvalidosViramErroSemExecutarAFerramenta() {
		ToolOutcome r = executa("eco", "não é json", TEMPO, 1000);

		assertThat(r.ok()).isFalse();
		assertThat(r.output()).startsWith("A ferramenta falhou:");
		assertThat(conjunto.execucoes).hasValue(0);
	}

	@Test
	void tempoEsgotadoInterrompeAFerramentaEVoltaComoErro() {
		ToolOutcome r = executa("lenta", "{\"ms\":10000}", Duration.ofMillis(150), 1000);

		assertThat(r.ok()).isFalse();
		assertThat(r.output()).isEqualTo("A ferramenta passou de 150 ms e foi interrompida.");
		assertThat(r.durationMs()).isBetween(100L, 3000L);
	}

	@Test
	void saidaVaziaViraAviso() {
		ToolOutcome r = executa("vazia", "{}", TEMPO, 1000);

		assertThat(r.ok()).isTrue();
		assertThat(r.output()).isEqualTo("(sem saída)");
	}

	@Test
	void mensagensSaemNoIdiomaPedido() {
		ToolOutcome r = executor.execute(null, "voa", List.of("eco"), "{}", TEMPO, 1000, AgentTexts.of(Language.EN))
			.block();

		assertThat(r.output()).isEqualTo("The tool 'voa' does not exist. Available: eco.");
	}

	private ToolOutcome executa(String nome, String argumentos, Duration tempo, int maxSaida) {
		ToolCallback ferramenta = registro.find(nome).orElseThrow();
		return executor.execute(ferramenta, nome, registro.names(), argumentos, tempo, maxSaida, pt)
			.block(Duration.ofSeconds(15));
	}
}
