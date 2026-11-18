package com.example.java_mind_agent.agent.prompt;

import org.jspecify.annotations.Nullable;

/**
 * Ponto de extensão do prompt de sistema: cada contribuidor escreve uma seção que o {@link SystemPromptBuilder} cola
 * depois do texto-base, na ordem de {@link org.springframework.core.annotation.Order}. É assim que a versão do Java
 * (F10) entra no prompt, e que os modos de ensino (F19) entrarão sem mexer no agente: basta um {@code @Component}
 * que implemente esta interface.
 */
@FunctionalInterface
public interface PromptContributor {

	/**
	 * @return a seção (em Markdown, com título), ou {@code null} se não há nada a dizer neste contexto
	 */
	@Nullable String contribute(PromptContext context);
}
