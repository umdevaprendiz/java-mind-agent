package com.example.java_mind_agent.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS restrito às origens configuradas em {@code app.cors.allowed-origins}.
 * Sem origens configuradas, nenhuma origem externa é liberada.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

	private final AppProperties properties;

	WebConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		var origins = properties.cors().allowedOrigins();
		if (origins.isEmpty()) {
			return;
		}
		registry.addMapping("/api/**")
				.allowedOrigins(origins.toArray(String[]::new))
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.maxAge(3600);
	}
}
