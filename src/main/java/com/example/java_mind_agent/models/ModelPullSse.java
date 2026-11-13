package com.example.java_mind_agent.models;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.codec.ServerSentEvent;

import reactor.core.publisher.Flux;

/**
 * Traduz {@link PullEvent} para os eventos SSE do contrato ({@code ModelPullStreamEvent}): {@code progress},
 * {@code done} e {@code error}. O nome em {@code event:} é igual ao {@code type} do JSON em {@code data:}.
 */
final class ModelPullSse {

	private ModelPullSse() {
	}

	/** Eventos do download e, se ele falhar, um evento {@code error} final em vez de derrubar a conexão. */
	static Flux<ServerSentEvent<Map<String, Object>>> toSse(Flux<PullEvent> events) {
		return events.map(ModelPullSse::toSse).onErrorResume(error -> Flux.just(ModelStreamSse.errorEvent(error)));
	}

	private static ServerSentEvent<Map<String, Object>> toSse(PullEvent event) {
		return switch (event) {
			case PullEvent.Progress progress -> {
				// null explícito (e não campo ausente) enquanto o tamanho não é conhecido: o contrato aceita os dois.
				Map<String, Object> fields = new LinkedHashMap<>();
				fields.put("status", progress.status());
				fields.put("completedBytes", progress.completedBytes());
				fields.put("totalBytes", progress.totalBytes());
				fields.put("percent", progress.percent());
				yield ModelStreamSse.sse("progress", fields);
			}
			case PullEvent.Done done -> ModelStreamSse.sse("done", Map.of());
		};
	}
}
