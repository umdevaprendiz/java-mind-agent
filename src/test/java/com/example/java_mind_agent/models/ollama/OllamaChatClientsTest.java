package com.example.java_mind_agent.models.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Token;
import com.example.java_mind_agent.support.ModelsTestData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.micrometer.observation.ObservationRegistry;

/**
 * O chat vai para o Ollama do tier, que pode ser outro computador: o servidor padrão e um "remoto" falsos, com o
 * protocolo real ({@code POST /api/chat} com NDJSON), mostram quem recebe cada pedido.
 */
class OllamaChatClientsTest {

	private static final List<Message> PERGUNTA = List.of(new UserMessage("O que é um record?"));

	private static final String FIM = """
			{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":""},\
			"done":true,"done_reason":"stop","total_duration":5000000000,"load_duration":1000000000,\
			"prompt_eval_count":26,"prompt_eval_duration":300000000,"eval_count":42,"eval_duration":2100000000}""";

	private static WireMockServer padrao;

	private static WireMockServer remoto;

	private OllamaChatClients clientes;

	private OllamaChatService servico;

	@BeforeAll
	static void sobemOsOllamasFalsos() {
		padrao = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		padrao.start();
		remoto = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		remoto.start();
	}

	@AfterAll
	static void desligamOsOllamasFalsos() {
		padrao.stop();
		remoto.stop();
	}

	@BeforeEach
	void preparaOServico() {
		padrao.resetAll();
		remoto.resetAll();
		responde(padrao, "Resposta do padrão");
		responde(remoto, "Resposta do remoto");
		clientes = clientes(padrao.baseUrl());
		servico = new OllamaChatService(clientes, ModelsTestData.properties());
	}

	@Test
	void semHostFalaComOOllamaPadrao() {
		List<ModelStreamEvent> eventos = servico.stream("m", PERGUNTA, GenerationOptions.defaults()).collectList().block();

		assertThat(eventos).contains(new Token("Resposta do padrão"));
		padrao.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
		assertThat(remoto.getAllServeEvents()).isEmpty();
	}

	@Test
	void comHostFalaComOOllamaDaqueleHostEComONomeDoModeloPedido() {
		List<ModelStreamEvent> eventos = servico.stream(remoto.baseUrl(), "qwen3:32b", PERGUNTA,
				GenerationOptions.defaults()).collectList().block();

		assertThat(eventos).contains(new Token("Resposta do remoto"));
		assertThat(eventos.getLast()).isInstanceOf(Completed.class);
		remoto.verify(1, postRequestedFor(urlEqualTo("/api/chat"))
			.withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:32b")))
			.withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("O que é um record?"))));
		assertThat(padrao.getAllServeEvents()).as("o padrão não recebe o pedido do tier remoto").isEmpty();
	}

	@Test
	void hostComBarraNoFimOuIgualAoPadraoCaiNoClienteCerto() {
		servico.stream(remoto.baseUrl() + "/", "m", PERGUNTA, GenerationOptions.defaults()).blockLast();
		servico.stream(padrao.baseUrl() + "/", "m", PERGUNTA, GenerationOptions.defaults()).blockLast();

		remoto.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
		padrao.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
	}

	@Test
	void umClientePorHostEReaproveitadoEOPadraoEOAutoconfigurado() {
		ChatClient doPadrao = clientes.forHost(null);
		ChatClient doRemoto = clientes.forHost(remoto.baseUrl());

		assertThat(clientes.forHost(padrao.baseUrl())).isSameAs(doPadrao);
		assertThat(clientes.forHost(padrao.baseUrl() + "/")).isSameAs(doPadrao);
		assertThat(clientes.forHost(remoto.baseUrl())).isSameAs(doRemoto);
		assertThat(clientes.forHost(remoto.baseUrl() + "/")).isSameAs(doRemoto);
		assertThat(doRemoto).isNotSameAs(doPadrao);
	}

	@Test
	void doisHostsRemotosDiferentesNaoSeMisturam() throws IOException {
		WireMockServer outro = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		outro.start();
		try {
			responde(outro, "Resposta do outro");

			List<ModelStreamEvent> doRemoto = servico.stream(remoto.baseUrl(), "m", PERGUNTA, GenerationOptions.defaults())
				.collectList()
				.block();
			List<ModelStreamEvent> doOutro = servico.stream(outro.baseUrl(), "m", PERGUNTA, GenerationOptions.defaults())
				.collectList()
				.block();

			assertThat(doRemoto).contains(new Token("Resposta do remoto")).doesNotContain(new Token("Resposta do outro"));
			assertThat(doOutro).contains(new Token("Resposta do outro"));
		}
		finally {
			outro.stop();
		}
	}

	@Test
	void hostRemotoForaDoArViraOllamaUnavailableSemCairNoPadrao() throws IOException {
		String fora = "http://localhost:" + portaFechada();

		Throwable erro = org.assertj.core.api.Assertions
			.catchThrowable(() -> servico.stream(fora, "m", PERGUNTA, GenerationOptions.defaults()).blockLast());

		assertThat(erro).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).isTrue();
		});
		assertThat(padrao.getAllServeEvents()).isEmpty();
	}

	// ------------------------------------------------------------------- auxiliares

	private static OllamaChatClients clientes(String baseUrlPadrao) {
		OllamaApi api = OllamaApi.builder().baseUrl(baseUrlPadrao).build();
		ChatClient.Builder construtor = ChatClient.builder(OllamaChatModel.builder().ollamaApi(api).build());
		DefaultListableBeanFactory vazio = new DefaultListableBeanFactory(); // sem beans: valem os padrões do Spring AI
		return new OllamaChatClients(construtor, new OllamaHosts(baseUrlPadrao),
				vazio.getBeanProvider(RestClient.Builder.class), vazio.getBeanProvider(WebClient.Builder.class),
				ToolCallingManager.builder().build(), vazio.getBeanProvider(ObservationRegistry.class),
				vazio.getBeanProvider(RetryTemplate.class));
	}

	private static void responde(WireMockServer servidor, String texto) {
		String token = """
				{"model":"m","created_at":"2026-11-12T22:00:00Z","message":{"role":"assistant","content":"%s"},"done":false}"""
			.formatted(texto);
		servidor.stubFor(post("/api/chat").willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
			.withBody(token + "\n" + FIM + "\n")));
	}

	private static int portaFechada() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
