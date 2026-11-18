package com.example.java_mind_agent.settings;

import org.jspecify.annotations.Nullable;

import com.example.java_mind_agent.models.Tier;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Preferências do usuário (schema {@code Settings} do contrato).
 *
 * @param defaultTier        tier usado quando a requisição não escolhe um
 * @param defaultJavaVersion versão major do Java quando a conversa não fixa uma; {@code null} = automático
 * @param language           idioma da interface e das respostas
 */
public record Settings(
		@NotNull Tier defaultTier,
		@Nullable @Min(1) @Max(99) Integer defaultJavaVersion,
		@NotNull Language language) {
}
