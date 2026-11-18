package com.example.java_mind_agent.common.error;

/**
 * Códigos de erro estáveis da API (schema {@code ErrorCode} de {@code api/openapi.yaml}). O front decide a mensagem e
 * a ação (ex.: oferecer baixar o modelo) a partir daqui, não do texto. O {@code ApiContractTest} garante que esta lista
 * e a do contrato são iguais.
 *
 * <p>Cada código tem o status HTTP com que a API responde quando o erro acontece antes de um stream começar (depois
 * disso o erro vai como evento {@code error} dentro do stream, que já respondeu 200).
 */
public enum ErrorCode {

	VALIDATION_FAILED(400),
	NOT_FOUND(404),
	CONFLICT(409),
	PAYLOAD_TOO_LARGE(413),
	UNSUPPORTED_MEDIA_TYPE(415),
	/** O modelo existe na configuração mas não foi baixado: dá para resolver baixando. */
	MODEL_NOT_INSTALLED(422),
	MODEL_DOES_NOT_FIT(422),
	OLLAMA_UNAVAILABLE(503),
	SANDBOX_UNAVAILABLE(503),
	PDF_NOT_READY(409),
	JOB_NOT_ACTIVE(409),
	GENERATION_TIMEOUT(504),
	/** O cliente desistiu (fechou a conexão); convenção 499 do nginx, que o HTTP padrão não define. */
	CANCELLED(499),
	INTERNAL_ERROR(500);

	private final int httpStatus;

	ErrorCode(int httpStatus) {
		this.httpStatus = httpStatus;
	}

	public int httpStatus() {
		return httpStatus;
	}
}
