package com.example.java_mind_agent.models;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.java_mind_agent.common.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz o que pode dar errado ao falar com o Ollama (rede, HTTP, tempo) para {@link ModelException} com código
 * estável e mensagem em português. Nunca troca de modelo em silêncio: só descreve o problema.
 */
final class OllamaErrorMapper {

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
