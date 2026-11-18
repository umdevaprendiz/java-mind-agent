package com.example.java_mind_agent.models.ollama;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz o que pode dar errado ao falar com o Ollama (rede, HTTP, tempo) para {@link ModelException} com código
 * estável e mensagem em português. Nunca troca de modelo em silêncio: só descreve o problema.
 */
public final class OllamaErrorMapper {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private OllamaErrorMapper() {
	}

	static ModelException map(String model, Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof ModelException e) {
				return e;
			}
			if (t instanceof WebClientResponseException e) {
				return fromResponse(model, e);
			}
			if (t instanceof TimeoutException) {
				return new ModelException(ErrorCode.GENERATION_TIMEOUT,
						"O modelo '" + model + "' demorou demais para responder.", true, error);
			}
			// WebClientRequestException cobre falha de conexão; IOException cobre conexão recusada ou cortada no
			// meio (ex.: o Ollama foi encerrado por falta de memória no meio da resposta).
			if (t instanceof WebClientRequestException || t instanceof IOException) {
				return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
						"Não consegui falar com o Ollama. Confira se ele está em execução.", true, error);
			}
		}
		return new ModelException(ErrorCode.INTERNAL_ERROR,
				"Erro inesperado ao gerar a resposta: " + rootMessage(error), false, error);
	}

	private static ModelException fromResponse(String model, WebClientResponseException e) {
		int status = e.getStatusCode().value();
		String detail = ollamaMessage(e.getResponseBodyAsString());
		String lower = detail.toLowerCase(Locale.ROOT);

		if (status == 404 || (lower.contains("model") && lower.contains("not found"))) {
			return new ModelException(ErrorCode.MODEL_NOT_INSTALLED,
					"O modelo '" + model + "' não está instalado no Ollama. Baixe-o antes de usar.", false, e);
		}
		if (lower.contains("more system memory") || lower.contains("out of memory")
				|| lower.contains("not enough memory")) {
			return new ModelException(ErrorCode.MODEL_DOES_NOT_FIT,
					"O modelo '" + model + "' não cabe na memória disponível. Feche outros programas ou escolha "
							+ "um nível menor. (" + detail + ")",
					false, e);
		}
		if (status == 400) {
			return new ModelException(ErrorCode.VALIDATION_FAILED,
					"O Ollama recusou o pedido para o modelo '" + model + "': " + detail, false, e);
		}
		if (status == 429 || status == 503) {
			return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE, "O Ollama está ocupado. Tente de novo em instantes.",
					true, e);
		}
		return new ModelException(ErrorCode.INTERNAL_ERROR,
				"O Ollama devolveu um erro (HTTP " + status + "): " + detail, status >= 502, e);
	}

	/**
	 * Erros de um <b>download</b> ({@code /api/pull}). Diferem dos de uma geração: um 404 aqui é "esse nome não existe
	 * no registro", não "o modelo não está instalado", e quase todo erro de rede se resolve baixando de novo, porque o
	 * Ollama guarda o que já baixou.
	 */
	public static ModelException mapPull(String model, Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof ModelException e) {
				return e;
			}
			if (t instanceof WebClientResponseException e) {
				return fromPullMessage(model, ollamaMessage(e.getResponseBodyAsString()), e.getStatusCode().value());
			}
			if (t instanceof TimeoutException) {
				return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
						"O download de '" + model + "' ficou sem receber dados e foi dado como travado. Baixe de novo: "
								+ "ele continua de onde parou.",
						true, error);
			}
			if (t instanceof WebClientRequestException || t instanceof IOException) {
				return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
						"A conexão com o Ollama caiu durante o download de '" + model + "'. Confira se ele está em "
								+ "execução e baixe de novo: continua de onde parou.",
						true, error);
			}
		}
		return new ModelException(ErrorCode.INTERNAL_ERROR,
				"Erro inesperado ao baixar '" + model + "': " + rootMessage(error), false, error);
	}

	/** Classifica a mensagem de erro que o Ollama manda no corpo ou numa linha {@code {"error":"..."}} do stream. */
	public static ModelException fromPullMessage(String model, String message, int status) {
		String detail = message == null ? "" : message;
		String lower = detail.toLowerCase(Locale.ROOT);

		if (lower.contains("file does not exist") || lower.contains("manifest unknown") || lower.contains("not found")
				|| status == 404) {
			return new ModelException(ErrorCode.NOT_FOUND, "O modelo '" + model + "' não existe no registro do Ollama. "
					+ "Confira o nome em app.models (ollama-model).", false);
		}
		if (lower.contains("no space left") || lower.contains("not enough space") || lower.contains("disk full")) {
			return new ModelException(ErrorCode.INTERNAL_ERROR,
					"Sem espaço em disco para baixar '" + model + "'. Libere espaço e baixe de novo.", false);
		}
		if (lower.contains("digest mismatch") || lower.contains("unexpected eof")) {
			return new ModelException(ErrorCode.INTERNAL_ERROR,
					"O arquivo baixado de '" + model + "' veio corrompido. Baixe de novo. (" + detail + ")", true);
		}
		if (lower.contains("dial tcp") || lower.contains("no such host") || lower.contains("i/o timeout")
				|| lower.contains("connection refused") || lower.contains("connection reset") || lower.contains("tls")
				|| lower.contains("timeout") || lower.contains("eof") || status == 429 || status == 503) {
			return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
					"O Ollama não conseguiu baixar '" + model + "': sem internet ou o registro está fora do ar. Baixe de "
							+ "novo: continua de onde parou. (" + detail + ")",
					true);
		}
		return new ModelException(ErrorCode.INTERNAL_ERROR,
				"O Ollama não conseguiu baixar '" + model + "': " + (detail.isBlank() ? "HTTP " + status : detail),
				status >= 502);
	}

	/** O Ollama responde erros como {@code {"error":"mensagem"}}; se não vier assim, devolve o corpo como está. */
	private static String ollamaMessage(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			String message = JSON.readTree(body).path("error").asString("");
			return message.isBlank() ? body.strip() : message;
		}
		catch (RuntimeException notJson) {
			return body.strip();
		}
	}

	private static String rootMessage(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}
}
