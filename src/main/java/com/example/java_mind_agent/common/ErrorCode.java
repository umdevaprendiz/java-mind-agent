package com.example.java_mind_agent.common;

/**
 * Códigos de erro estáveis da API (schema {@code ErrorCode} de {@code api/openapi.yaml}). O front decide a mensagem e
 * a ação (ex.: oferecer baixar o modelo) a partir daqui, não do texto. O {@code ApiContractTest} garante que esta lista
 * e a do contrato são iguais.
 */
public enum ErrorCode {

	VALIDATION_FAILED,
	NOT_FOUND,
	CONFLICT,
	PAYLOAD_TOO_LARGE,
	UNSUPPORTED_MEDIA_TYPE,
	MODEL_NOT_INSTALLED,
	MODEL_DOES_NOT_FIT,
	OLLAMA_UNAVAILABLE,
	SANDBOX_UNAVAILABLE,
	PDF_NOT_READY,
	JOB_NOT_ACTIVE,
	GENERATION_TIMEOUT,
	CANCELLED,
	INTERNAL_ERROR
}
