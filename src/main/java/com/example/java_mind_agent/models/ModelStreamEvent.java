package com.example.java_mind_agent.models;

import org.jspecify.annotations.Nullable;

/**
 * O que o cliente Ollama entrega durante uma geração. Um stream termina com {@link Completed}, com erro
 * ({@link ModelException}) ou porque o assinante cancelou; nesse último caso nada mais é emitido.
 * Quem expõe isso na API (chat, F14) traduz para os eventos SSE do contrato.
 */
public sealed interface ModelStreamEvent {

	/** Pedaço da resposta final, a ser concatenado. */
	record Token(String text) implements ModelStreamEvent {
	}

	/** Pedaço do raciocínio do modelo (modelos com suporte e {@code think} ligado); não faz parte da resposta. */
	record Thinking(String text) implements ModelStreamEvent {
	}

	/** Fim normal da geração, com as métricas medidas. */
	record Completed(String model, FinishReason finishReason, Usage usage) implements ModelStreamEvent {
	}

	enum FinishReason {
		/** O modelo terminou sozinho. */
		STOP,
		/** Parou por atingir o limite de tokens ({@code numPredict}) ou a janela de contexto. */
		LENGTH
	}

	/**
	 * Métricas de uma geração.
	 *
	 * @param promptTokens       tokens lidos do prompt
	 * @param completionTokens   tokens gerados (inclui o raciocínio)
	 * @param tokensPerSecond    velocidade de geração informada pelo Ollama ({@code eval_count / eval_duration});
	 *                           {@code null} se não deu para calcular
	 * @param timeToFirstTokenMs do início do pedido até o primeiro pedaço de resposta ou raciocínio, inclusive
	 *                           carregar o modelo; {@code null} se nada foi gerado
	 * @param loadDurationMs     quanto o Ollama levou para carregar o modelo (0 se já estava na memória)
	 * @param totalDurationMs    tempo total medido por este lado, do pedido ao último pedaço
	 */
	record Usage(
			int promptTokens,
			int completionTokens,
			@Nullable Double tokensPerSecond,
			@Nullable Long timeToFirstTokenMs,
			@Nullable Long loadDurationMs,
			long totalDurationMs) {
	}
}
