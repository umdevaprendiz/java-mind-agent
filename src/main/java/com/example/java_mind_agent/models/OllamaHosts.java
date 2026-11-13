package com.example.java_mind_agent.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Os servidores Ollama que o app conhece: o padrão ({@code spring.ai.ollama.base-url}) e os que algum tier aponta
 * com {@code base-url} próprio (ex.: o Extra em outro computador com GPU).
 */
@Component
public class OllamaHosts {

	private final String defaultBaseUrl;

	private final Map<String, OllamaAdminClient> clients = new ConcurrentHashMap<>();

	public OllamaHosts(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String defaultBaseUrl) {
		this.defaultBaseUrl = normalize(defaultBaseUrl);
	}

	/** URL do host a usar: a informada ou, se vazia, a padrão; sem barra no fim, para comparar e usar como chave. */
	public String resolve(@Nullable String baseUrl) {
		return baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl : normalize(baseUrl);
	}

	public boolean isDefault(String resolvedBaseUrl) {
		return defaultBaseUrl.equals(resolvedBaseUrl);
	}

	public OllamaAdminClient client(@Nullable String baseUrl) {
		return clients.computeIfAbsent(resolve(baseUrl), OllamaAdminClient::new);
	}

	private static String normalize(String url) {
		String stripped = url.strip();
		return stripped.endsWith("/") ? stripped.substring(0, stripped.length() - 1) : stripped;
	}
}
