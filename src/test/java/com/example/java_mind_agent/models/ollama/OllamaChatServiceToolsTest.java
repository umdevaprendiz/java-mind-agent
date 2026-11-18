package com.example.java_mind_agent.models.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Token;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCallRequest;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCalls;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Ferramentas no cliente Ollama: o modelo as recebe no pedido e o pedido de chamada volta como evento, mas o serviço
 * nunca executa nada (quem executa é o agente).
 */
class OllamaChatServiceToolsTest {

	private static final List<Message> PERGUNTA = List.of(new UserMessage("Que horas são em Recife?"));

	private static final String FIM = """
			{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":""},\
			"done":true,"done_reason":"stop","total_duration":5000000000,"load_duration":1000000000,\
			"prompt_eval_count":26,"prompt_eval_duration":300000000,"eval_count":42,"eval_duration":2100000000}""";

	private static final String PEDIDO_DE_FERRAMENTA = """
			{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"",\
			"tool_calls":[{"function":{"name":"horaEm","arguments":{"cidade":"Recife"}}}]},"done":false}""";

	private static WireMockServer ollama;

	private final AtomicInteger execucoes = new AtomicInteger();

	private List<ToolCallback> ferramentas;

	private OllamaChatService service;

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
	void preparaServico() {
		ollama.resetAll();
		execucoes.set(0);
		ferramentas = List.of(ToolCallbacks.from(new Relogio()));
		service = OllamaChatServiceTest.servico(ollama.baseUrl());
	}

	/** Ferramenta de teste: conta quantas vezes foi de fato executada. */
	class Relogio {

		@Tool(description = "Diz que horas são numa cidade")
		public String horaEm(@ToolParam(description = "nome da cidade") String cidade) {
			execucoes.incrementAndGet();
			return "10:00 em " + cidade;
		}

	}

	@Test
	void ofereceAsFerramentasAoModeloComNomeDescricaoEParametros() {
		responde(token("ok"), FIM);

		coleta(service.stream(null, "m", PERGUNTA, GenerationOptions.defaults(), ferramentas));

		ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
			.withRequestBody(matchingJsonPath("$.tools[0].type", equalTo("function")))
			.withRequestBody(matchingJsonPath("$.tools[0].function.name", equalTo("horaEm")))
			.withRequestBody(matchingJsonPath("$.tools[0].function.description", equalTo("Diz que horas são numa cidade")))
			.withRequestBody(matchingJsonPath("$.tools[0].function.parameters.properties.cidade.type", equalTo("string"))));
	}

	@Test
	void relataOPedidoDeChamadaAntesDoFimSemExecutarAFerramenta() {
		responde(PEDIDO_DE_FERRAMENTA, FIM);

		List<ModelStreamEvent> eventos = coleta(
				service.stream(null, "m", PERGUNTA, GenerationOptions.defaults(), ferramentas));

		assertThat(eventos).hasSize(2);
		assertThat(eventos.get(0)).isEqualTo(new ToolCalls(List.of(new ToolCallRequest("horaEm", "{\"cidade\":\"Recife\"}"))));
		assertThat(eventos.get(1)).isInstanceOf(Completed.class);
		assertThat(execucoes).as("o serviço só relata; quem executa é o agente").hasValue(0);
		ollama.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
	}

	@Test
	void textoDitoAntesDoPedidoContinuaChegandoComoToken() {
		responde(token("Vou consultar. "), PEDIDO_DE_FERRAMENTA, FIM);

		List<ModelStreamEvent> eventos = coleta(
				service.stream(null, "m", PERGUNTA, GenerationOptions.defaults(), ferramentas));

		assertThat(eventos).hasSize(3);
		assertThat(eventos.get(0)).isEqualTo(new Token("Vou consultar. "));
		assertThat(eventos.get(1)).isInstanceOf(ToolCalls.class);
	}

	@Test
	void semFerramentasOModeloNaoRecebeNenhumaDefinicaoENuncaHaPedidoDeChamada() {
		responde(token("ok"), FIM);

		List<ModelStreamEvent> eventos = coleta(service.stream("m", PERGUNTA, GenerationOptions.defaults()));

		assertThat(eventos).noneMatch(ToolCalls.class::isInstance);
		String corpo = ollama.findAll(postRequestedFor(urlEqualTo("/api/chat"))).getFirst().getBodyAsString();
		assertThat(corpo).contains("\"tools\":[]").doesNotContain("function");
	}

	// ------------------------------------------------------------------- auxiliares

	private static void responde(String... linhas) {
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(String.join("\n", linhas) + "\n")));
	}

	private static String token(String texto) {
		return """
				{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"%s"},"done":false}"""
			.formatted(texto);
	}

	private static List<ModelStreamEvent> coleta(reactor.core.publisher.Flux<ModelStreamEvent> fluxo) {
		return fluxo.collectList().block(Duration.ofSeconds(15));
	}
}
