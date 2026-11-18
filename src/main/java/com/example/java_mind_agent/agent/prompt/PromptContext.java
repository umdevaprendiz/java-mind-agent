package com.example.java_mind_agent.agent.prompt;

import org.jspecify.annotations.Nullable;

import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.settings.Language;

/**
 * O que um {@link PromptContributor} pode consultar para escrever a sua parte do prompt de sistema.
 *
 * @param language     idioma da resposta
 * @param message      a pergunta da pessoa
 * @param javaVersion  a versão do Java que a resposta vai assumir (F10)
 * @param teaching     modo de ensino pedido (F19); {@code null} = o padrão
 * @param toolsEnabled o modelo vai receber ferramentas nesta execução
 */
public record PromptContext(
		Language language,
		String message,
		AssumedVersion javaVersion,
		@Nullable String teaching,
		boolean toolsEnabled) {
}
