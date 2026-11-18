package com.example.java_mind_agent.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.models.ModelException;

/** Os campos extras de um erro ({@code tier}, {@code suggestedTier}...) que o front usa para agir sem ler o texto. */
class ApiExceptionTest {

	@Test
	void semCamposExtrasOMapaVemVazio() {
		assertThat(new ApiException(ErrorCode.NOT_FOUND, "x", false).getDetails()).isEmpty();
	}

	@Test
	void guardaOsCamposNaOrdemEmQueForamAcrescentados() {
		ApiException e = new ApiException(ErrorCode.MODEL_NOT_INSTALLED, "x", false).detail("tier", "strong")
			.detail("suggestedTier", "medium");

		assertThat(e.getDetails()).containsExactly(org.assertj.core.api.Assertions.entry("tier", "strong"),
				org.assertj.core.api.Assertions.entry("suggestedTier", "medium"));
	}

	@Test
	void oMapaDevolvidoNaoPodeSerAlteradoDeFora() {
		ApiException e = new ApiException(ErrorCode.NOT_FOUND, "x", false).detail("tier", "low");

		assertThatThrownBy(() -> e.getDetails().put("outro", "valor")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void modelExceptionContinuaSendoModelExceptionDepoisDeAcrescentarCampos() {
		ModelException e = new ModelException(ErrorCode.MODEL_DOES_NOT_FIT, "x", false).detail("tier", "extra");

		assertThat(e.getDetails()).containsEntry("tier", "extra");
		assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT);
	}

	@Test
	void umNovoValorParaAMesmaChaveSubstituiOAntigo() {
		ApiException e = new ApiException(ErrorCode.NOT_FOUND, "x", false).detail("tier", "low").detail("tier", "medium");

		assertThat(e.getDetails()).containsExactly(org.assertj.core.api.Assertions.entry("tier", "medium"));
	}
}
