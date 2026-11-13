package com.example.java_mind_agent.common;

import org.jspecify.annotations.Nullable;

/**
 * Erro de negócio que a API devolve ao front como {@code problem+json} (ou como evento {@code error}, dentro de um
 * stream), já com um {@link ErrorCode} estável e uma mensagem em português pronta para mostrar ao usuário.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final boolean retryable;

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
}
