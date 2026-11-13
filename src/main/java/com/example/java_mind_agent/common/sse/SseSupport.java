package com.example.java_mind_agent.common.sse;

import java.time.Duration;

import org.springframework.http.codec.ServerSentEvent;

import reactor.core.publisher.Flux;

/**
 * Utilidades para os streams SSE da API (chat, download de modelo, progresso de PDF e de lote).
 */
public final class SseSupport {

	/** Intervalo dos comentários de keep-alive descrito em {@code api/openapi.yaml}. */
	public static final Duration KEEP_ALIVE = Duration.ofSeconds(15);

	private SseSupport() {
	}

	/**
	 * Intercala um comentário {@code : keep-alive} a cada {@code interval} enquanto {@code events} não termina.
	 * Em CPU, carregar um modelo leva dezenas de segundos sem nenhum evento; sem isso, navegador, proxy ou antivírus
	 * podem derrubar a conexão por inatividade. Os comentários param sozinhos quando {@code events} termina, dá erro
	 * ou é cancelado.
	 */
	public static <T> Flux<ServerSentEvent<T>> withKeepAlive(Flux<ServerSentEvent<T>> events, Duration interval) {
		return events.publish(shared -> Flux.merge(shared,
				Flux.interval(interval)
					.<ServerSentEvent<T>>map(tick -> ServerSentEvent.<T>builder().comment("keep-alive").build())
					.takeUntilOther(shared.ignoreElements())));
	}

	public static <T> Flux<ServerSentEvent<T>> withKeepAlive(Flux<ServerSentEvent<T>> events) {
		return withKeepAlive(events, KEEP_ALIVE);
	}
}
