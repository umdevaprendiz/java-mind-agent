package com.example.java_mind_agent.models.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;

class OllamaErrorMapperTest {

	@Test
	void modeloNaoBaixadoViraModelNotInstalled() {
		ModelException e = map(http(404, "{\"error\":\"model \\\"qwen2.5:7b\\\" not found, try pulling it first\"}"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED);
		assertThat(e.isRetryable()).isFalse();
		assertThat(e.getMessage()).contains("'m'").contains("não está instalado");
	}

	@Test
	void faltaDeMemoriaViraModelDoesNotFitEPreservaODetalhe() {
		ModelException e = map(http(500,
				"{\"error\":\"model requires more system memory (9.1 GiB) than is available (4.2 GiB)\"}"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT);
		assertThat(e.isRetryable()).isFalse();
		assertThat(e.getMessage()).contains("9.1 GiB").contains("4.2 GiB");
	}

	@Test
	void pedidoRecusadoPeloOllamaViraValidationFailed() {
		ModelException e = map(http(400, "{\"error\":\"\\\"m\\\" does not support thinking\"}"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(e.getMessage()).contains("does not support thinking");
	}

	@Test
	void ollamaOcupadoViraOllamaUnavailableComNovaTentativa() {
		ModelException e = map(http(503, "{\"error\":\"server busy, please try again\"}"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
		assertThat(e.isRetryable()).isTrue();
	}

	@Test
	void erroGenericoDoOllamaViraInternalErrorComOStatus() {
		ModelException e500 = map(http(500, "{\"error\":\"algo quebrou\"}"));
		ModelException e502 = map(http(502, "bad gateway"));

		assertThat(e500.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(e500.isRetryable()).isFalse();
		assertThat(e500.getMessage()).contains("HTTP 500").contains("algo quebrou");
		assertThat(e502.isRetryable()).isTrue();
		assertThat(e502.getMessage()).contains("bad gateway");
	}

	@Test
	void conexaoRecusadaViraOllamaUnavailable() {
		var recusada = new WebClientRequestException(new ConnectException("Connection refused"), HttpMethod.POST,
				URI.create("http://localhost:11434/api/chat"), new HttpHeaders());

		ModelException e = map(recusada);

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getCause()).isSameAs(recusada);
	}

	@Test
	void conexaoCortadaNoMeioDaRespostaViraOllamaUnavailable() {
		ModelException e = map(new IllegalStateException("falha", new IOException("Connection reset")));

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
	}

	@Test
	void tempoEsgotadoViraGenerationTimeoutComNovaTentativa() {
		ModelException e = map(new TimeoutException("sem resposta"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.GENERATION_TIMEOUT);
		assertThat(e.isRetryable()).isTrue();
	}

	@Test
	void modelExceptionPassaSemSerEmbrulhada() {
		var original = new ModelException(ErrorCode.CANCELLED, "x", false);

		assertThat(map(new RuntimeException("externo", original))).isSameAs(original);
	}

	@Test
	void falhaDesconhecidaViraInternalErrorComACausaRaiz() {
		ModelException e = map(new IllegalArgumentException("topo", new IllegalStateException("causa real")));

		assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(e.isRetryable()).isFalse();
		assertThat(e.getMessage()).contains("causa real");
	}

	// ------------------------------------------------------------------ download (pull)

	@Test
	void nomeQueNaoExisteNoRegistroNaoViraModeloNaoInstalado() {
		ModelException e = mapPull(http(404, "{\"error\":\"pull model manifest: file does not exist\"}"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(e.isRetryable()).isFalse();
		assertThat(e.getMessage()).contains("'m'").contains("não existe no registro").contains("ollama-model");
	}

	@Test
	void semEspacoEmDiscoNaoValeTentarDeNovoSemLiberarEspaco() {
		ModelException e = ollamaDisseNoStream("write /root/.ollama/blobs/x: no space left on device");

		assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(e.isRetryable()).isFalse();
		assertThat(e.getMessage()).contains("Sem espaço em disco").contains("'m'");
	}

	@Test
	void arquivoCorrompidoPedeParaBaixarDeNovo() {
		ModelException e = ollamaDisseNoStream("digest mismatch: expected sha256:aaa, got sha256:bbb");

		assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getMessage()).contains("corrompido");
	}

	@Test
	void semInternetOuRegistroForaDoArEhOllamaUnavailableComRetomada() {
		for (String mensagem : new String[] { "pull model manifest: Get \"https://registry.ollama.ai/v2/library/m/manifests/latest\": dial tcp: lookup registry.ollama.ai: no such host",
				"read tcp 10.0.0.2:5000->104.21.0.1:443: i/o timeout", "net/http: TLS handshake timeout" }) {
			ModelException e = ollamaDisseNoStream(mensagem);

			assertThat(e.getCode()).as(mensagem).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).as(mensagem).isTrue();
			assertThat(e.getMessage()).as(mensagem).contains("continua de onde parou");
		}
	}

	@Test
	void erroDesconhecidoDoDownloadMostraAMensagemDoOllama() {
		ModelException e = ollamaDisseNoStream("algo inesperado");

		assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(e.isRetryable()).isFalse();
		assertThat(e.getMessage()).contains("algo inesperado");
	}

	@Test
	void downloadSemDadosPorMuitoTempoEhTravamentoComRetomada() {
		ModelException e = mapPull(new TimeoutException("idle"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getMessage()).contains("travado").contains("continua de onde parou");
	}

	@Test
	void conexaoQueCaiDuranteODownloadAvisaQueDaParaRetomar() {
		ModelException e = mapPull(new IllegalStateException("x", new IOException("Connection prematurely closed")));

		assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
		assertThat(e.isRetryable()).isTrue();
		assertThat(e.getMessage()).contains("caiu durante o download").contains("continua de onde parou");
	}

	@Test
	void modelExceptionNoDownloadPassaSemSerEmbrulhada() {
		var original = new ModelException(ErrorCode.CONFLICT, "x", false);

		assertThat(mapPull(new RuntimeException("externo", original))).isSameAs(original);
	}

	@Test
	void falhaDesconhecidaNoDownloadViraInternalError() {
		ModelException e = mapPull(new IllegalStateException("causa real"));

		assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(e.getMessage()).contains("causa real");
	}

	private static ModelException ollamaDisseNoStream(String mensagem) {
		return OllamaErrorMapper.fromPullMessage("m", mensagem, 200);
	}

	private static ModelException mapPull(Throwable error) {
		return OllamaErrorMapper.mapPull("m", error);
	}

	private static ModelException map(Throwable error) {
		return OllamaErrorMapper.map("m", error);
	}

	private static WebClientResponseException http(int status, String body) {
		return WebClientResponseException.create(status, "status", new HttpHeaders(),
				body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}
}
