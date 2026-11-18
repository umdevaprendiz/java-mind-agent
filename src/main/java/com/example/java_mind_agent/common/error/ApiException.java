package com.example.java_mind_agent.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Erro de negócio que a API devolve ao front como {@code problem+json} (ou como evento {@code error}, dentro de um
 * stream), já com um {@link ErrorCode} estável e uma mensagem em português pronta para mostrar ao usuário.
 *
 * <p>Alguns erros trazem também {@linkplain #getDetails() campos extras} que o front usa para agir sem interpretar o
 * texto (ex.: {@code suggestedTier}, o nível menor que dá para usar agora).
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final boolean retryable;

	private final Map<String, Object> details = new LinkedHashMap<>();

	public ApiException(ErrorCode code, String message, boolean retryable) {
		this(code, message, retryable, null);
	}

	public ApiException(ErrorCode code, String message, boolean retryable, @Nullable Throwable cause) {
		super(message, cause);
		this.code = code;
		this.retryable = retryable;
	}

	public ErrorCode getCode() {
		return code;
	}

	/** Vale tentar de novo sem mudar nada (Ollama ocupado, desligado ou lento), em oposição a um erro permanente. */
	public boolean isRetryable() {
		return retryable;
	}

	/** Campos extras do erro, na ordem em que foram adicionados; saem junto de {@code code} no JSON. */
	public Map<String, Object> getDetails() {
		return Collections.unmodifiableMap(details);
	}

	/** Acrescenta um campo extra ao erro. */
	public ApiException detail(String key, Object value) {
		details.put(key, value);
		return this;
	}
}
