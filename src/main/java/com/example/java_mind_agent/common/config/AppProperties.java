package com.example.java_mind_agent.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração tipada da aplicação (prefixo {@code app}).
 * Novas seções (tiers de modelo, sandbox, corpus...) entram aqui nas respectivas features.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors) {

	public AppProperties {
		if (cors == null) {
			cors = new Cors(List.of());
		}
	}

	/**
	 * @param allowedOrigins origens autorizadas a chamar {@code /api/**}
	 */
	public record Cors(List<String> allowedOrigins) {

		public Cors {
			allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
		}
	}
}
