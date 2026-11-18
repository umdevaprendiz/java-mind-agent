package com.example.java_mind_agent.models.catalog;

import java.util.Locale;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;

/**
 * Os três motivos pelos quais um modelo não pode ser usado agora, com a mesma mensagem em qualquer lugar que apareçam:
 * o gerenciador (F07) e o roteador (F08).
 */
public final class ModelProblems {

	private ModelProblems() {
	}

	public static ModelException unreachable(ModelSpec spec) {
		return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
				"Não consegui falar com o Ollama em " + spec.host() + ". Confira se ele está em execução.", true);
	}

	public static ModelException doesNotFit(ModelSpec spec, double usableRamGb) {
		return new ModelException(ErrorCode.MODEL_DOES_NOT_FIT,
				"O modelo '%s' (%s) pede ~%s GB de RAM e esta máquina tem %s GB utilizáveis. Escolha um nível menor ou "
					.formatted(spec.model(), spec.label(), gb(spec.minRamGb()), gb(usableRamGb))
						+ "aponte esse nível para outro Ollama (base-url).",
				false);
	}

	public static ModelException notInstalled(ModelSpec spec) {
		return new ModelException(ErrorCode.MODEL_NOT_INSTALLED,
				"O modelo '" + spec.model() + "' (" + spec.label() + ") não está instalado. Baixe-o antes.", false);
	}

	static String gb(double value) {
		return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value);
	}
}
