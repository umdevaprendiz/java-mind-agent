package com.example.java_mind_agent.knowledge;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do módulo de conhecimento (prefixo {@code app.knowledge}).
 *
 * @param defaultJavaVersion versão assumida quando nada indica outra; vazio = a LTS mais recente do registro
 * @param legacyJavaVersion  versão assumida quando a pessoa fala em "versão antiga" sem dizer qual
 */
@ConfigurationProperties(prefix = "app.knowledge")
public record KnowledgeProperties(
		@Nullable Integer defaultJavaVersion,
		@DefaultValue("8") int legacyJavaVersion) {
}
