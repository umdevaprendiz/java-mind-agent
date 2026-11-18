package com.example.java_mind_agent.models.manager;

import org.jspecify.annotations.Nullable;

import com.example.java_mind_agent.models.ModelException;

/**
 * O que o download de um modelo conta ao resto do app. Os erros não são eventos: viajam como {@link ModelException}
 * (o controller os transforma em {@code problem+json} antes do stream começar, ou em evento {@code error} depois).
 */
public sealed interface PullEvent {

	/**
	 * Andamento do download, somando todas as camadas do modelo.
	 *
	 * @param status         {@code downloading} enquanto baixa; nas demais etapas, o texto do Ollama
	 *                       ({@code pulling manifest}, {@code verifying sha256 digest}, {@code writing manifest}...)
	 * @param completedBytes bytes já baixados; {@code null} antes de saber o tamanho
	 * @param totalBytes     bytes a baixar entre as camadas já conhecidas; cresce quando uma camada nova aparece
	 * @param percent        0 a 100 com uma casa decimal; {@code null} antes de saber o tamanho
	 */
	record Progress(String status, @Nullable Long completedBytes, @Nullable Long totalBytes, @Nullable Double percent)
			implements PullEvent {
	}

	/** O modelo foi baixado, verificado e registrado. */
	record Done() implements PullEvent {
	}
}
