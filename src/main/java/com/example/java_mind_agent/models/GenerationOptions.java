package com.example.java_mind_agent.models;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Ajustes de uma geração. Campo {@code null} = usar o padrão de {@link ModelsProperties.Generation}.
 *
 * @param temperature      0 a 2; menor = mais determinístico
 * @param numCtx           janela de contexto, em tokens
 * @param numPredict       máximo de tokens gerados; {@code null} = sem limite
 * @param keepAlive        quanto tempo manter o modelo na memória (ex.: {@code 10m}, {@code 0} descarrega já)
 * @param think            liga/desliga o raciocínio do modelo; {@code null} = padrão do modelo (modelos sem suporte
 *                         recusam {@code true})
 * @param firstTokenTimeout espera máxima pelo primeiro pedaço da resposta
 * @param idleTimeout      silêncio máximo entre pedaços
 * @param maxDuration      duração total máxima; {@link Duration#ZERO} desliga
 */
public record GenerationOptions(
		@Nullable Double temperature,
		@Nullable Integer numCtx,
		@Nullable Integer numPredict,
		@Nullable String keepAlive,
		@Nullable Boolean think,
		@Nullable Duration firstTokenTimeout,
		@Nullable Duration idleTimeout,
		@Nullable Duration maxDuration) {

	private static final GenerationOptions DEFAULTS = new GenerationOptions(null, null, null, null, null, null, null,
			null);

	public static GenerationOptions defaults() {
		return DEFAULTS;
	}
}
