package com.example.java_mind_agent.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** O status HTTP de cada código é parte do contrato com o front: mudar um deles sem querer quebra o tratamento de erro. */
class ErrorCodeTest {

	@Test
	void todoCodigoTemUmStatusDeErroHttpValido() {
		assertThat(ErrorCode.values()).allSatisfy(code -> assertThat(code.httpStatus()).as(code.name())
			.isBetween(400, 599));
	}

	@Test
	void osStatusQueOFrontEOsTestesDependem() {
		assertThat(ErrorCode.VALIDATION_FAILED.httpStatus()).isEqualTo(400);
		assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
		assertThat(ErrorCode.CONFLICT.httpStatus()).isEqualTo(409);
		assertThat(ErrorCode.MODEL_NOT_INSTALLED.httpStatus()).as("não instalado é 422, não 503").isEqualTo(422);
		assertThat(ErrorCode.MODEL_DOES_NOT_FIT.httpStatus()).isEqualTo(422);
		assertThat(ErrorCode.OLLAMA_UNAVAILABLE.httpStatus()).isEqualTo(503);
		assertThat(ErrorCode.GENERATION_TIMEOUT.httpStatus()).isEqualTo(504);
		assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
	}

	@Test
	void errosDeConflitoDeEstadoSaoTodos409() {
		assertThat(Arrays.asList(ErrorCode.CONFLICT, ErrorCode.PDF_NOT_READY, ErrorCode.JOB_NOT_ACTIVE))
			.extracting(ErrorCode::httpStatus)
			.containsOnly(409);
	}
}
