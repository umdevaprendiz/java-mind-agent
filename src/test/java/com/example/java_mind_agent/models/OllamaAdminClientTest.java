package com.example.java_mind_agent.models;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.common.ErrorCode;
import com.example.java_mind_agent.models.OllamaAdminClient.InstalledModel;
import com.example.java_mind_agent.models.OllamaAdminClient.LoadedModel;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/** As perguntas rápidas ao Ollama, contra um servidor falso que devolve o JSON no formato real da API. */
class OllamaAdminClientTest {

	private static WireMockServer ollama;

	private OllamaAdminClient client;

	@BeforeAll
	static void sobeOOllamaFalso() {
		ollama = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		ollama.start();
	}

	@AfterAll
	static void desligaOOllamaFalso() {
		ollama.stop();
	}

	@BeforeEach
	void limpa() {
		ollama.resetAll();
		client = new OllamaAdminClient(ollama.baseUrl());
	}

	private static com.github.tomakehurst.wiremock.client.MappingBuilder json(
			com.github.tomakehurst.wiremock.client.MappingBuilder mapping, String corpo) {
		return mapping.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(corpo));
	}

	@Test
	void listaOsModelosInstaladosIgnorandoCamposQueNaoConhece() {
		ollama.stubFor(json(get("/api/tags"), """
				{"models":[
				  {"name":"qwen2.5:7b","model":"qwen2.5:7b","modified_at":"2026-11-01T10:00:00Z","size":4683087332,
				   "digest":"845dbda0ea48","details":{"family":"qwen2","parameter_size":"7.6B","quantization_level":"Q4_K_M"}},
				  {"name":"bge-m3:latest","size":1157672605,"digest":"790764642607"}
				]}"""));

		assertThat(client.listInstalled()).containsExactly(
				new InstalledModel("qwen2.5:7b", 4683087332L, "845dbda0ea48"),
				new InstalledModel("bge-m3:latest", 1157672605L, "790764642607"));
	}

	@Test
	void semModelosDevolveListaVaziaEmVezDeFalhar() {
		ollama.stubFor(json(get("/api/tags"), "{\"models\":[]}"));
		assertThat(client.listInstalled()).isEmpty();

		ollama.stubFor(json(get("/api/tags"), "{}"));
		assertThat(client.listInstalled()).isEmpty();
	}

	@Test
	void listaOsModelosCarregadosEQuantoDeCadaUmEstaNaGpu() {
		ollama.stubFor(json(get("/api/ps"), """
				{"models":[{"name":"qwen2.5:7b","size":6000000000,"size_vram":0,"expires_at":"2026-11-12T23:30:00Z"}]}"""));

		assertThat(client.listLoaded()).containsExactly(new LoadedModel("qwen2.5:7b", 6000000000L, 0));
	}

	@Test
	void leAsCapacidadesDoModelo() {
		ollama.stubFor(json(post("/api/show"), """
				{"license":"...","details":{"family":"qwen2"},"capabilities":["completion","tools"]}"""));

		assertThat(client.capabilities("qwen2.5:7b")).containsExactly("completion", "tools");
	}

	@Test
	void versoesAntigasQueNaoInformamCapacidadesDevolvemListaVazia() {
		ollama.stubFor(json(post("/api/show"), "{\"details\":{\"family\":\"qwen2\"}}"));

		assertThat(client.capabilities("qwen2.5:7b")).isEmpty();
	}

	@Test
	void modeloQueOHostNaoTemViraModeloNaoInstalado() {
		ollama.stubFor(post("/api/show").willReturn(aResponse().withStatus(404)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"model 'nao-existe' not found\"}")));

		assertThatThrownBy(() -> client.capabilities("nao-existe")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED));
	}

	@Test
	void leAVersaoDoServidor() {
		ollama.stubFor(json(get("/api/version"), "{\"version\":\"0.12.6\"}"));

		assertThat(client.version()).isEqualTo("0.12.6");
	}

	@Test
	void respostaHttpDeErroNumaPerguntaDoServidorNaoViraModeloNaoInstalado() {
		ollama.stubFor(get("/api/tags").willReturn(aResponse().withStatus(404).withBody("<html>nada aqui</html>")));

		assertThatThrownBy(() -> client.listInstalled()).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.getMessage()).contains("HTTP 404").contains(ollama.baseUrl());
			assertThat(e.isRetryable()).isFalse();
		});
	}

	@Test
	void erroDoServidorPodeSerTentadoDeNovo() {
		ollama.stubFor(get("/api/ps").willReturn(aResponse().withStatus(500)));

		assertThatThrownBy(() -> client.listLoaded()).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).isTrue();
		});
	}

	@Test
	void corpoVazioViraOllamaIndisponivel() {
		ollama.stubFor(get("/api/version").willReturn(aResponse().withStatus(200)));

		assertThatThrownBy(() -> client.version()).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE));
	}

	@Test
	void ollamaForaDoArViraOllamaIndisponivel() throws IOException {
		int portaFechada;
		try (ServerSocket socket = new ServerSocket(0)) {
			portaFechada = socket.getLocalPort();
		}
		OllamaAdminClient semServidor = new OllamaAdminClient("http://localhost:" + portaFechada);

		assertThatThrownBy(semServidor::listInstalled).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).isTrue();
		});
	}

	@Test
	void guardaAUrlDoHost() {
		assertThat(client.baseUrl()).isEqualTo(ollama.baseUrl());
	}
}
