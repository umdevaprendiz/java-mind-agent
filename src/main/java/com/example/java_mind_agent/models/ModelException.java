package com.example.java_mind_agent.models;

import org.jspecify.annotations.Nullable;

import com.example.java_mind_agent.common.error.ApiException;
import com.example.java_mind_agent.common.error.ErrorCode;

/**
 * Falha ao falar com o Ollama (ou ao gerenciar seus modelos), já traduzida para um {@link ErrorCode} do contrato e uma
 * mensagem em português pronta para mostrar ao usuário.
 */
public class ModelException extends ApiException {

	public ModelException(ErrorCode code, String message, boolean retryable) {
		super(code, message, retryable);
	}

	public ModelException(ErrorCode code, String message, boolean retryable, @Nullable Throwable cause) {
		super(code, message, retryable, cause);
	}

	@Override
	public ModelException detail(String key, Object value) {
		super.detail(key, value);
		return this;
	}
}
