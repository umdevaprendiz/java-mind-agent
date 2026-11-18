package com.example.java_mind_agent.models.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.OllamaAdminClient.InstalledModel;
import com.example.java_mind_agent.models.ollama.OllamaAdminClient.LoadedModel;
import com.example.java_mind_agent.models.ollama.OllamaAdminClient.PullLine;
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

	// ------------------------------------------------------------------ download (pull)

	private static final Duration SEM_LIMITE = Duration.ofSeconds(10);

	private static com.github.tomakehurst.wiremock.client.MappingBuilder ndjson(
			com.github.tomakehurst.wiremock.client.MappingBuilder mapping, String... linhas) {
		return mapping.willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
			.withBody(String.join("\n", linhas) + "\n"));
	}

	@Test
	void pullEntregaAsLinhasDeProgressoNaOrdemEmQueChegam() {
		ollama.stubFor(ndjson(post("/api/pull"), "{\"status\":\"pulling manifest\"}",
				"{\"status\":\"pulling 2bada8a74506\",\"digest\":\"sha256:2bada8a74506\",\"total\":4683087332,\"completed\":1048576}",
				"{\"status\":\"verifying sha256 digest\"}", "{\"status\":\"writing manifest\"}",
				"{\"status\":\"success\"}"));

		List<PullLine> linhas = client.pull("qwen2.5:7b", SEM_LIMITE).collectList().block();

		assertThat(linhas).containsExactly(new PullLine("pulling manifest", null, null, null, null),
				new PullLine("pulling 2bada8a74506", "sha256:2bada8a74506", 4683087332L, 1048576L, null),
				new PullLine("verifying sha256 digest", null, null, null, null),
				new PullLine("writing manifest", null, null, null, null),
				new PullLine("success", null, null, null, null));
		ollama.verify(postRequestedFor(urlEqualTo("/api/pull"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:7b\",\"stream\":true}")));
	}

	@Test
	void pullIgnoraLinhasEmBrancoECamposQueNaoConhece() {
		ollama.stubFor(ndjson(post("/api/pull"), "", "{\"status\":\"pulling manifest\",\"novo\":1}", "   ",
				"{\"status\":\"success\"}"));

		assertThat(client.pull("m", SEM_LIMITE).collectList().block()).extracting(PullLine::status)
			.containsExactly("pulling manifest", "success");
	}

	@Test
	void pullEntregaAMensagemDeErroDoStreamComoItem() {
		ollama.stubFor(ndjson(post("/api/pull"), "{\"status\":\"pulling manifest\"}",
				"{\"error\":\"pull model manifest: file does not exist\"}"));

		List<PullLine> linhas = client.pull("m", SEM_LIMITE).collectList().block();

		assertThat(linhas).extracting(PullLine::error).containsExactly(null, "pull model manifest: file does not exist");
	}

	@Test
	void pullComLinhaQueNaoEJsonViraErroInterno() {
		ollama.stubFor(ndjson(post("/api/pull"), "isto nao e json"));

		assertThatThrownBy(() -> client.pull("m", SEM_LIMITE).collectList().block())
			.isInstanceOfSatisfying(ModelException.class, e -> {
				assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
				assertThat(e.getMessage()).contains("isto nao e json");
			});
	}

	@Test
	void pullComErroHttpFalhaComAExcecaoDoWebClientParaOServicoMapear() {
		ollama.stubFor(post("/api/pull").willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

		assertThatThrownBy(() -> client.pull("m", SEM_LIMITE).collectList().block())
			.isInstanceOf(WebClientResponseException.class);
	}

	@Test
	void pullSemNenhumaLinhaPorMuitoTempoEstoura() {
		ollama.stubFor(post("/api/pull").willReturn(aResponse().withFixedDelay(2000)
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody("{\"status\":\"success\"}\n")));

		assertThatThrownBy(() -> client.pull("m", Duration.ofMillis(300)).collectList().block())
			.hasRootCauseInstanceOf(TimeoutException.class);
	}

	// ------------------------------------------------------------------ remover, carregar, descarregar

	@Test
	void deleteEnviaONomeDoModeloNoCorpo() {
		ollama.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(200)));

		client.delete("qwen2.5:3b");

		ollama.verify(deleteRequestedFor(urlEqualTo("/api/delete"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:3b\"}")));
	}

	@Test
	void deleteDeModeloQueNaoExisteViraModeloNaoInstalado() {
		ollama.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(404)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"model 'x' not found\"}")));

		assertThatThrownBy(() -> client.delete("x")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED));
	}

	@Test
	void carregarModeloDeChatUsaGenerateComKeepAliveEPromptVazio() {
		ollama.stubFor(post("/api/generate").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"model\":\"qwen2.5:3b\",\"done\":true}")));

		client.load("qwen2.5:3b", false, "10m", SEM_LIMITE);

		ollama.verify(postRequestedFor(urlEqualTo("/api/generate"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:3b\",\"keep_alive\":\"10m\",\"stream\":false}")));
	}

	@Test
	void carregarModeloDeEmbeddingUsaEmbedPorqueGenerateNaoAceita() {
		ollama.stubFor(post("/api/embed").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"model\":\"bge-m3\",\"embeddings\":[]}")));

		client.load("bge-m3", true, "10m", SEM_LIMITE);

		ollama.verify(postRequestedFor(urlEqualTo("/api/embed"))
			.withRequestBody(equalToJson("{\"model\":\"bge-m3\",\"input\":\"\",\"keep_alive\":\"10m\"}")));
		ollama.verify(0, postRequestedFor(urlEqualTo("/api/generate")));
	}

	@Test
	void descarregarEnviaKeepAliveZeroNumerico() {
		ollama.stubFor(post("/api/generate").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"done\":true}")));
		ollama.stubFor(post("/api/embed").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"embeddings\":[]}")));

		client.unload("qwen2.5:3b", false);
		client.unload("bge-m3", true);

		ollama.verify(postRequestedFor(urlEqualTo("/api/generate"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:3b\",\"keep_alive\":0,\"stream\":false}")));
		ollama.verify(postRequestedFor(urlEqualTo("/api/embed"))
			.withRequestBody(equalToJson("{\"model\":\"bge-m3\",\"input\":\"\",\"keep_alive\":0}")));
	}

	@Test
	void faltaDeMemoriaAoCarregarViraModelDoesNotFit() {
		ollama.stubFor(post("/api/generate").willReturn(aResponse().withStatus(500)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":\"model requires more system memory (9.1 GiB) than is available (4.2 GiB)\"}")));

		assertThatThrownBy(() -> client.load("qwen2.5:7b", false, "10m", SEM_LIMITE))
			.isInstanceOfSatisfying(ModelException.class,
					e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT));
	}

	@Test
	void carregamentoQueDemoraMaisQueOTetoViraGenerationTimeout() {
		ollama.stubFor(post("/api/generate").willReturn(aResponse().withFixedDelay(2000)
			.withStatus(200)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"done\":true}")));

		assertThatThrownBy(() -> client.load("qwen2.5:7b", false, "10m", Duration.ofMillis(300)))
			.isInstanceOfSatisfying(ModelException.class,
					e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GENERATION_TIMEOUT));
	}

	@Test
	void carregarSemOllamaViraOllamaIndisponivel() throws IOException {
		int portaFechada;
		try (ServerSocket socket = new ServerSocket(0)) {
			portaFechada = socket.getLocalPort();
		}
		OllamaAdminClient semServidor = new OllamaAdminClient("http://localhost:" + portaFechada);

		assertThatThrownBy(() -> semServidor.load("m", false, "10m", SEM_LIMITE))
			.isInstanceOfSatisfying(ModelException.class,
					e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE));
	}

	@Test
	void guardaAUrlDoHost() {
		assertThat(client.baseUrl()).isEqualTo(ollama.baseUrl());
	}
}
