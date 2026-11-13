package com.example.java_mind_agent.models;

import com.example.java_mind_agent.common.ErrorCode;

/**
 * Falha ao falar com o Ollama, já traduzida para um {@link ErrorCode} do contrato e uma mensagem em português
 * pronta para mostrar ao usuário.
 */
public class ModelException extends RuntimeException {

	private final ErrorCode code;

	private final boolean retryable;

	public ModelException(ErrorCode code, String message, boolean retryable) {
		this(code, message, retryable, null);
	}

	public ModelException(ErrorCode code, String message, boolean retryable, Throwable cause) {
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
