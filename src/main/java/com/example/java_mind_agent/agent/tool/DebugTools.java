package com.example.java_mind_agent.agent.tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.knowledge.version.JavaRelease;
import com.example.java_mind_agent.knowledge.version.JavaVersionRegistry;

/**
 * Ferramentas de demonstração, só com {@code app.agent.debug.endpoint-enabled=true} (perfil dev): servem para ver o
 * loop de ferramentas funcionando antes de existirem as de verdade (executar Java, buscar na documentação...).
 */
@Component
@ConditionalOnProperty(prefix = "app.agent.debug", name = "endpoint-enabled", havingValue = "true")
class DebugTools implements AgentToolSet {

	private final JavaVersionRegistry versions;

	DebugTools(JavaVersionRegistry versions) {
		this.versions = versions;
	}

	@Tool(name = "java_release_info", description = """
			Dá os fatos de uma versão do Java: ano de lançamento, se é LTS, os recursos principais, o que foi removido \
			e o que foi depreciado nela. Use para perguntas sobre o que mudou ou existe em uma versão.""")
	public String javaReleaseInfo(@ToolParam(description = "número da versão do Java, por exemplo 17") int version) {
		return versions.find(version).map(DebugTools::describe)
			.orElse("Não tenho dados da versão " + version + ". Versões conhecidas: de 1 a "
					+ versions.latest().version() + ".");
	}

	@Tool(name = "current_date_time", description = "Diz a data e a hora atuais deste computador.")
	public String currentDateTime() {
		return ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private static String describe(JavaRelease release) {
		return "%s (%d)%s\n- recursos: %s\n- removido: %s\n- depreciado: %s".formatted(release.label(),
				release.releaseYear(), release.lts() ? ", LTS" : "", list(release.highlights()),
				list(release.removed()), list(release.deprecated()));
	}

	private static String list(java.util.List<String> items) {
		return items.isEmpty() ? "nada registrado" : items.stream().collect(Collectors.joining("; "));
	}
}
