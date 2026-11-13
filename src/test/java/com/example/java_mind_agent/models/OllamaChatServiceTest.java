package com.example.java_mind_agent.models;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;

import com.example.java_mind_agent.common.ErrorCode;
import com.example.java_mind_agent.models.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ModelStreamEvent.FinishReason;
import com.example.java_mind_agent.models.ModelStreamEvent.Thinking;
import com.example.java_mind_agent.models.ModelStreamEvent.Token;
import com.example.java_mind_agent.support.ModelsTestData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import reactor.core.publisher.Flux;

/**
 * O cliente Ollama contra um servidor falso que fala o protocolo real ({@code POST /api/chat} com NDJSON), sem Spring
 * nem Docker. O cancelamento fica em {@link OllamaChatServiceCancellationTest}.
 */
class OllamaChatServiceTest {

	private static final List<Message> PERGUNTA = List.of(new UserMessage("O que é um record?"));

	private static final String FIM = """
			{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":""},\
			"done":true,"done_reason":"stop","total_duration":5000000000,"load_duration":1000000000,\
			"prompt_eval_count":26,"prompt_eval_duration":300000000,"eval_count":42,"eval_duration":2100000000}""";

	private static WireMockServer ollama;

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
		service = servico(ollama.baseUrl());
	}

	// -------------------------------------------------------------- streaming e métricas

	@Test
	void emiteOsTokensNaOrdemEFechaComUsoEMetricasDoOllama() {
		responde(token("Um "), token("record "), token("é uma classe de dados."), FIM);

		List<ModelStreamEvent> eventos = coleta(service.stream("m", PERGUNTA, GenerationOptions.defaults()));

		assertThat(eventos).hasSize(4);
		assertThat(eventos.subList(0, 3)).containsExactly(new Token("Um "), new Token("record "),
				new Token("é uma classe de dados."));
		Completed fim = (Completed) eventos.get(3);
		assertThat(fim.model()).isEqualTo("m");
		assertThat(fim.finishReason()).isEqualTo(FinishReason.STOP);
		assertThat(fim.usage().promptTokens()).isEqualTo(26);
		assertThat(fim.usage().completionTokens()).isEqualTo(42);
		assertThat(fim.usage().tokensPerSecond()).as("42 tokens em 2,1 s").isCloseTo(20.0, within(0.01));
		assertThat(fim.usage().loadDurationMs()).isEqualTo(1000);
		assertThat(fim.usage().timeToFirstTokenMs()).isNotNull().isLessThanOrEqualTo(fim.usage().totalDurationMs());
	}

	@Test
	void medeOTempoAteOPrimeiroTokenIncluindoACargaDoModelo() {
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(token("Olá") + "\n" + FIM + "\n")
			.withFixedDelay(400)));

		Completed fim = (Completed) coleta(service.stream("m", PERGUNTA, GenerationOptions.defaults())).getLast();

		assertThat(fim.usage().timeToFirstTokenMs()).isGreaterThanOrEqualTo(350);
		assertThat(fim.usage().totalDurationMs()).isGreaterThanOrEqualTo(fim.usage().timeToFirstTokenMs());
	}

	@Test
	void separaORaciocinioDaResposta() {
		responde(pensamento("Hmm, "), pensamento("records."), token("É imutável."), FIM);

		List<ModelStreamEvent> eventos = coleta(
				service.stream("m", PERGUNTA, new GenerationOptions(null, null, null, null, true, null, null, null)));

		assertThat(eventos.subList(0, 3)).containsExactly(new Thinking("Hmm, "), new Thinking("records."),
				new Token("É imutável."));
	}

	@Test
	void paradaPorLimiteDeTokensViraFinishReasonLength() {
		responde(token("Um"), FIM.replace("\"done_reason\":\"stop\"", "\"done_reason\":\"length\""));

		Completed fim = (Completed) coleta(service.stream("m", PERGUNTA, GenerationOptions.defaults())).getLast();

		assertThat(fim.finishReason()).isEqualTo(FinishReason.LENGTH);
	}

	// ----------------------------------------------------------------- opções do pedido

	@Test
	void enviaMensagensEOpcoesPadraoDaConfiguracao() {
		responde(token("ok"), FIM);

		coleta(service.stream("qwen2.5:1.5b", PERGUNTA, GenerationOptions.defaults()));

		ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
			.withRequestBody(matchingJsonPath("$.model", equalTo("qwen2.5:1.5b")))
			.withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
			.withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("user")))
			.withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("O que é um record?")))
			.withRequestBody(matchingJsonPath("$.options.temperature", equalTo("0.3")))
			.withRequestBody(matchingJsonPath("$.options.num_ctx", equalTo("4096")))
			.withRequestBody(matchingJsonPath("$.keep_alive", equalTo("10m"))));
	}

	@Test
	void opcoesDaRequisicaoSobrescrevemOPadrao() {
		responde(token("ok"), FIM);

		coleta(service.stream("m", PERGUNTA,
				new GenerationOptions(0.9, 8192, 100, "0", false, null, null, null)));

		ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
			.withRequestBody(matchingJsonPath("$.options.temperature", equalTo("0.9")))
			.withRequestBody(matchingJsonPath("$.options.num_ctx", equalTo("8192")))
			.withRequestBody(matchingJsonPath("$.options.num_predict", equalTo("100")))
			.withRequestBody(matchingJsonPath("$.keep_alive", equalTo("0")))
			.withRequestBody(matchingJsonPath("$.think", equalTo("false"))));
	}

	// -------------------------------------------------------------------------- erros

	@Test
	void modeloNaoInstaladoViraModelNotInstalled() {
		ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(404)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"model \\\"zzz\\\" not found, try pulling it first\"}")));

		ModelException e = falha(service.stream("zzz", PERGUNTA, GenerationOptions.defaults()));

		assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED);
		assertThat(e.getMessage()).contains("'zzz'");
	}

	@Test
	void modeloQueNaoCabeNaMemoriaViraModelDoesNotFit() {
		ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(500)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"model requires more system memory (9.1 GiB) than is available (4.2 GiB)\"}")));

		ModelException e = falha(service.stream("m", PERGUNTA, GenerationOptions.defaults()));

		assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT);
	}

	@Test
	void ollamaDesligadoViraOllamaUnavailable() throws IOException {
		int portaSemNinguem;
		try (ServerSocket socket = new ServerSocket(0)) {
			portaSemNinguem = socket.getLocalPort();
		}

		ModelException e = falha(
				servico("http://localhost:" + portaSemNinguem).stream("m", PERGUNTA, GenerationOptions.defaults()));

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
		assertThat(e.isRetryable()).isTrue();
	}

	@Test
	void respostaQueTerminaSemOFimDoOllamaViraErroEmVezDeSucessoFalso() {
		responde(token("Um "), token("record"));

		ModelException e = falha(service.stream("m", PERGUNTA, GenerationOptions.defaults()));

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
		assertThat(e.getMessage()).contains("interrompida");
	}

	// ------------------------------------------------------------------------ tempo

	@Test
	void semPrimeiroPedacoNoPrazoViraGenerationTimeout() {
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(token("tarde") + "\n" + FIM + "\n")
			.withFixedDelay(3000)));
		var rapido = new GenerationOptions(null, null, null, null, null, Duration.ofMillis(300), null, null);

		ModelException e = falha(service.stream("m", PERGUNTA, rapido));

		assertThat(e.getCode()).isEqualTo(ErrorCode.GENERATION_TIMEOUT);
		assertThat(e.isRetryable()).isTrue();
	}

	@Test
	void duracaoMaximaInterrompeAGeracaoMesmoComTokensChegando() {
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(token("a") + "\n" + token("b") + "\n" + token("c") + "\n" + token("d") + "\n" + FIM + "\n")
			.withChunkedDribbleDelay(5, 4000)));
		var comLimite = new GenerationOptions(null, null, null, null, null, null, null, Duration.ofMillis(800));

		ModelException e = falha(service.stream("m", PERGUNTA, comLimite));

		assertThat(e.getCode()).isEqualTo(ErrorCode.GENERATION_TIMEOUT);
	}

	// ------------------------------------------------------------------- auxiliares

	static OllamaChatService servico(String baseUrl) {
		OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
		OllamaChatModel model = OllamaChatModel.builder().ollamaApi(api).build();
		return new OllamaChatService(ChatClient.builder(model), ModelsTestData.properties());
	}

	static void responde(String... linhas) {
		ollama.stubFor(post("/api/chat").willReturn(aResponse()
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(String.join("\n", linhas) + "\n")));
	}

	static String token(String texto) {
		return """
				{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"%s"},"done":false}"""
			.formatted(texto);
	}

	static String pensamento(String texto) {
		return """
				{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"","thinking":"%s"},\
				"done":false}"""
			.formatted(texto);
	}

	private static List<ModelStreamEvent> coleta(Flux<ModelStreamEvent> fluxo) {
		return fluxo.collectList().block(Duration.ofSeconds(15));
	}

	private static ModelException falha(Flux<ModelStreamEvent> fluxo) {
		try {
			fluxo.collectList().block(Duration.ofSeconds(15));
		}
		catch (ModelException e) {
			return e;
		}
		throw new AssertionError("era esperada uma ModelException");
	}
}
