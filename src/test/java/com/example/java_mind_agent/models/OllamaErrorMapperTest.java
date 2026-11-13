package com.example.java_mind_agent.models;

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

import com.example.java_mind_agent.common.ErrorCode;

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

	private static ModelException map(Throwable error) {
		return OllamaErrorMapper.map("m", error);
	}

	private static WebClientResponseException http(int status, String body) {
		return WebClientResponseException.create(status, "status", new HttpHeaders(),
				body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}
}
