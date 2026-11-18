package com.example.java_mind_agent.models.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.agent.AgentEvent;
import com.example.java_mind_agent.agent.AgentEvent.FinishReason;
import com.example.java_mind_agent.agent.AgentEvent.Finished;
import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.agent.AgentEvent.Status;
import com.example.java_mind_agent.agent.AgentEvent.ToolCall;
import com.example.java_mind_agent.agent.AgentEvent.ToolResult;
import com.example.java_mind_agent.agent.AgentEvent.Usage;
import com.example.java_mind_agent.agent.AgentProperties;
import com.example.java_mind_agent.agent.AgentLimits;
import com.example.java_mind_agent.agent.AgentRequest;
import com.example.java_mind_agent.agent.AgentRun;
import com.example.java_mind_agent.agent.AgentService;
import com.example.java_mind_agent.agent.FerramentasDeTeste;
import com.example.java_mind_agent.agent.loop.AgentLoop;
import com.example.java_mind_agent.agent.prompt.JavaVersionPromptContributor;
import com.example.java_mind_agent.agent.prompt.SystemPromptBuilder;
import com.example.java_mind_agent.agent.tool.ToolExecutor;
import com.example.java_mind_agent.agent.tool.ToolRegistry;
import com.example.java_mind_agent.knowledge.KnowledgeProperties;
import com.example.java_mind_agent.knowledge.version.JavaVersionDetector;
import com.example.java_mind_agent.knowledge.version.JavaVersionRegistry;
import com.example.java_mind_agent.knowledge.version.JavaVersionService;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.models.catalog.ModelCatalogService;
import com.example.java_mind_agent.models.routing.ModelRouter;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;
import com.example.java_mind_agent.settings.Settings;
import com.example.java_mind_agent.settings.SettingsService;
import com.example.java_mind_agent.support.ModelsTestData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import tools.jackson.databind.json.JsonMapper;

/**
 * O agente de ponta a ponta contra um Ollama falso que fala o protocolo real: o serviço do agente, o loop, o router e o
 * cliente Ollama de verdade; só o servidor é de mentira. Fica neste pacote porque o cliente de teste do Ollama é daqui.
 */
class AgentComOllamaFalsoTest {

	private static final String FIM = """
			{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":""},\
			"done":true,"done_reason":"stop","total_duration":5000000000,"load_duration":1000000000,\
			"prompt_eval_count":%d,"prompt_eval_duration":300000000,"eval_count":%d,"eval_duration":2000000000}""";

	private static final String PEDIDO_DE_FERRAMENTA = """
			{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"",\
			"tool_calls":[{"function":{"name":"eco","arguments":{"texto":"java"}}}]},"done":false}""";

	private static WireMockServer ollama;

	private final FerramentasDeTeste ferramentas = new FerramentasDeTeste();

	private AgentService agente;

	private ModelRouter router;

	@BeforeAll
	static void sobeOOllamaFalso() {
		ollama = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		ollama.start();
	}

	@AfterAll
	static void derrubaOOllamaFalso() {
		ollama.stop();
	}

	@BeforeEach
	void montaOAgente() {
		ollama.resetAll();
		router = spy(new ModelRouter(mock(ModelCatalogService.class), OllamaChatServiceTest.servico(ollama.baseUrl())));
		agenteCom(true);
	}

	private void agenteCom(Boolean suportaFerramentas) {
		doReturn(new Route(Tier.MEDIUM, "Médio", "m", ollama.baseUrl(), false, true, true, suportaFerramentas))
			.when(router).resolve(any());
		SettingsService preferencias = mock(SettingsService.class);
		org.mockito.Mockito.when(preferencias.get()).thenReturn(new Settings(Tier.MEDIUM, null, Language.PT_BR));
		JavaVersionRegistry registro = new JavaVersionRegistry();
		JavaVersionService versoes = new JavaVersionService(registro, new JavaVersionDetector(registro),
				new KnowledgeProperties(null, 8));
		JsonMapper json = JsonMapper.builder().build();
		ToolRegistry registroDeFerramentas = new ToolRegistry(List.of(ferramentas));
		AgentLoop loop = new AgentLoop(router, registroDeFerramentas, new ToolExecutor(json), json);
		agente = new AgentService(router, preferencias, versoes,
				new SystemPromptBuilder(List.of(new JavaVersionPromptContributor())), registroDeFerramentas, loop,
				new AgentProperties(new AgentLimits(6, 10, 4096, Duration.ofMinutes(2), Duration.ofSeconds(10), 8000),
						new AgentProperties.Debug(false)));
	}

	@Test
	void perguntaSimplesRespondeEmStreamingSabendoAVersaoDoJava() {
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(String.join("\n", token("Um "), token("record "), FIM.formatted(40, 12)) + "\n")));

		AgentRun run = agente.prepare(AgentRequest.of("No Java 21, o que é um record?"));
		List<AgentEvent> eventos = coleta(run);

		assertThat(run.javaVersion().version()).isEqualTo(21);
		assertThat(eventos.getFirst())
			.isEqualTo(new AgentEvent.Token("Versão do Java assumida: Java 21 (citada na pergunta).\n\n"));
		assertThat(eventos).containsSubsequence(new AgentEvent.Token("Um "), new AgentEvent.Token("record "));
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, null, 1, 0));
		Usage uso = (Usage) eventos.get(eventos.size() - 2);
		assertThat(uso.promptTokens()).isEqualTo(40);
		assertThat(uso.completionTokens()).isEqualTo(12);

		// o que o Ollama recebeu: o prompt de sistema com a versão, depois a pergunta
		String corpo = pedidosAoOllama().getFirst();
		assertThat(corpo).contains("\"role\":\"system\"").contains("Versão do Java assumida: Java 21 (citada na pergunta).")
			.contains("\"role\":\"user\"").contains("No Java 21, o que é um record?");
	}

	@Test
	void ferramentaPedidaPeloModeloEExecutadaESeuResultadoVoltaAoModelo() {
		ollama.stubFor(post("/api/chat").inScenario("loop")
			.whenScenarioStateIs(Scenario.STARTED)
			.willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
				.withBody(String.join("\n", token("Vou repetir. "), PEDIDO_DE_FERRAMENTA, FIM.formatted(50, 20)) + "\n"))
			.willSetStateTo("com resultado"));
		ollama.stubFor(post("/api/chat").inScenario("loop")
			.whenScenarioStateIs("com resultado")
			.willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
				.withBody(String.join("\n", token("Deu JAVA."), FIM.formatted(90, 6)) + "\n")));

		List<AgentEvent> eventos = coleta(agente.prepare(AgentRequest.of("Repita 'java' em maiúsculas.")));

		assertThat(ferramentas.textosRecebidos).containsExactly("java");
		assertThat(eventos).containsSubsequence(
				new AgentEvent.Token("Vou repetir. "),
				new Status(Phase.EXECUTING_CODE, "Usando a ferramenta eco"),
				new ToolCall("call_1", "eco", java.util.Map.of("texto", "java")),
				new AgentEvent.Token("Deu JAVA."));
		assertThat(eventos).filteredOn(ToolResult.class::isInstance).singleElement().satisfies(e -> {
			ToolResult r = (ToolResult) e;
			assertThat(r.ok()).isTrue();
			assertThat(r.output()).isEqualTo("JAVA");
		});
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, null, 2, 1));
		Usage uso = (Usage) eventos.get(eventos.size() - 2);
		assertThat(uso.promptTokens()).isEqualTo(140);
		assertThat(uso.completionTokens()).isEqualTo(26);

		List<String> pedidos = pedidosAoOllama();
		assertThat(pedidos).hasSize(2);
		assertThat(pedidos.get(0)).contains("\"name\":\"eco\"").contains("Repete o texto recebido");
		// o segundo pedido leva o histórico completo: o que o modelo pediu e o resultado da ferramenta
		assertThat(pedidos.get(1)).contains("\"role\":\"assistant\"").contains("\"tool_calls\"")
			.contains("\"role\":\"tool\"").contains("JAVA");
	}

	@Test
	void modeloSemSuporteAFerramentasNaoRecebeFerramentasERespondeComAviso() {
		agenteCom(false);
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(String.join("\n", token("ok"), FIM.formatted(5, 1)) + "\n")));

		AgentRun run = agente.prepare(AgentRequest.of("Oi"));
		List<AgentEvent> eventos = coleta(run);

		assertThat(run.toolsEnabled()).isFalse();
		assertThat(eventos.getFirst()).isInstanceOfSatisfying(Status.class,
				s -> assertThat(s.message()).contains("não confirmou suporte a ferramentas"));
		assertThat(pedidosAoOllama().getFirst()).contains("\"tools\":[]");
	}

	// ----------------------------------------------------------------------------- auxiliares

	private static List<AgentEvent> coleta(AgentRun run) {
		return run.events().collectList().block(Duration.ofSeconds(20));
	}

	private static List<String> pedidosAoOllama() {
		return ollama.findAll(postRequestedFor(urlEqualTo("/api/chat"))).stream()
			.map(r -> r.getBodyAsString())
			.toList();
	}

	private static String token(String texto) {
		return """
				{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"%s"},"done":false}"""
			.formatted(texto);
	}
}
