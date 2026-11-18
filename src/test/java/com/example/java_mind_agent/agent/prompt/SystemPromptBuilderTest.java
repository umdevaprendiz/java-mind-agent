package com.example.java_mind_agent.agent.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.knowledge.KnowledgeProperties;
import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.knowledge.version.JavaVersionDetector;
import com.example.java_mind_agent.knowledge.version.JavaVersionRegistry;
import com.example.java_mind_agent.knowledge.version.JavaVersionService;
import com.example.java_mind_agent.settings.Language;

/** O prompt de sistema: texto-base por idioma, seguido das seções de quem estende. */
class SystemPromptBuilderTest {

	private final JavaVersionRegistry registry = new JavaVersionRegistry();

	private final JavaVersionService versions = new JavaVersionService(registry, new JavaVersionDetector(registry),
			new KnowledgeProperties(null, 8));

	private PromptContext contexto(Language idioma, String pergunta, Integer escolhida, String ensino) {
		AssumedVersion versao = versions.assume(pergunta, escolhida, null);
		return new PromptContext(idioma, pergunta, versao, ensino, true);
	}

	@Test
	void textoBaseNoIdiomaPedido() {
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of());

		String pt = builder.build(contexto(Language.PT_BR, "O que é um record?", null, null));
		String en = builder.build(contexto(Language.EN, "What is a record?", null, null));

		assertThat(pt).startsWith("Você é o Java Mind Agent").contains("## Como responder").contains("## Ferramentas");
		assertThat(en).startsWith("You are Java Mind Agent").contains("## How to answer").contains("## Tools");
	}

	@Test
	void cadaContribuidorAcrescentaASuaSecaoNaOrdemDada() {
		PromptContributor primeiro = c -> "## Primeiro\nA";
		PromptContributor segundo = c -> "## Segundo\nB";
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of(primeiro, segundo));

		String prompt = builder.build(contexto(Language.PT_BR, "x", null, null));

		assertThat(prompt).endsWith("## Primeiro\nA\n\n## Segundo\nB");
	}

	@Test
	void contribuidorSemNadaADizerEIgnorado() {
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of(c -> null, c -> "  ", c -> "## Só esta\nok"));

		String prompt = builder.build(contexto(Language.PT_BR, "x", null, null));

		assertThat(prompt).endsWith("\n\n## Só esta\nok").doesNotContain("\n\n\n");
	}

	@Test
	void contribuidorVeOModoDeEnsinoPedido() {
		PromptContributor ensino = c -> c.teaching() == null ? null : "## Modo de ensino\n" + c.teaching();
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of(ensino));

		assertThat(builder.build(contexto(Language.PT_BR, "x", null, "socratico"))).endsWith("## Modo de ensino\nsocratico");
		assertThat(builder.build(contexto(Language.PT_BR, "x", null, null))).doesNotContain("Modo de ensino");
	}

	// ------------------------------------------------------------------ a versão do Java (F10)

	@Test
	void aVersaoDoJavaEntraNoPromptComAFraseQueARespostaDeveDeclarar() {
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of(new JavaVersionPromptContributor()));

		String pt = builder.build(contexto(Language.PT_BR, "No Java 11, como uso var?", null, null));
		String en = builder.build(contexto(Language.EN, "In Java 11, how do I use var?", null, null));

		assertThat(pt).contains("## Versão do Java").contains("para Java 11.")
			.contains("não a repita")
			.contains("Versão do Java assumida: Java 11 (citada na pergunta).");
		assertThat(en).contains("## Java version").contains("for Java 11.")
			.contains("do not repeat it")
			.contains("Java version assumed: Java 11 (mentioned in the question).");
	}

	@Test
	void versaoInferidaDoCodigoDizOuMaisNova() {
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of(new JavaVersionPromptContributor()));

		String prompt = builder.build(contexto(Language.PT_BR, "record P(int x) { }", null, null));

		assertThat(prompt).contains("para Java 16 ou mais nova.").contains("Java 16 ou mais nova (o código usa record)");
	}

	@Test
	void aSecaoDaVersaoVemAntesDasOutras() {
		JavaVersionPromptContributor versao = new JavaVersionPromptContributor();
		SystemPromptBuilder builder = new SystemPromptBuilder(List.of(versao, c -> "## Depois\nx"));

		String prompt = builder.build(contexto(Language.PT_BR, "x", null, null));

		assertThat(prompt.indexOf("## Versão do Java")).isLessThan(prompt.indexOf("## Depois"));
	}
}
