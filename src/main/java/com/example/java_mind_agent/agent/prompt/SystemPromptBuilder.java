package com.example.java_mind_agent.agent.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.settings.Language;

/**
 * Monta o prompt de sistema do agente: o texto-base do idioma ({@code prompts/agent-system.<idioma>.md}) seguido da
 * seção de cada {@link PromptContributor}. O texto-base é lido na subida, então um arquivo faltando impede a
 * aplicação de subir em vez de estragar a primeira resposta.
 */
@Component
public class SystemPromptBuilder {

	private final Map<Language, String> base;

	private final List<PromptContributor> contributors;

	public SystemPromptBuilder(List<PromptContributor> contributors) {
		this.contributors = List.copyOf(contributors);
		this.base = Map.of(Language.PT_BR, load(Language.PT_BR), Language.EN, load(Language.EN));
	}

	/** O prompt completo para {@code context}. */
	public String build(PromptContext context) {
		List<String> sections = new ArrayList<>();
		sections.add(base.get(context.language()));
		for (PromptContributor contributor : contributors) {
			String section = contributor.contribute(context);
			if (section != null && !section.isBlank()) {
				sections.add(section.strip());
			}
		}
		return String.join("\n\n", sections);
	}

	private static String load(Language language) {
		ClassPathResource resource = new ClassPathResource("prompts/agent-system." + language.value() + ".md");
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Não consegui ler o prompt de sistema " + resource.getPath(), e);
		}
	}
}
