package com.example.java_mind_agent.models;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.codec.ServerSentEvent;

import com.example.java_mind_agent.common.ErrorCode;
import com.example.java_mind_agent.models.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ModelStreamEvent.FinishReason;
import com.example.java_mind_agent.models.ModelStreamEvent.Thinking;
import com.example.java_mind_agent.models.ModelStreamEvent.Token;

import reactor.core.publisher.Flux;

/**
 * Traduz {@link ModelStreamEvent} para eventos SSE com o mesmo formato do contrato ({@code api/openapi.yaml}):
 * o nome em {@code event:} é igual ao campo {@code type} do JSON em {@code data:}.
 */
final class ModelStreamSse {

	private ModelStreamSse() {
	}

	/** Eventos do stream e, se a geração falhar, um evento {@code error} final em vez de derrubar a conexão. */
	static Flux<ServerSentEvent<Map<String, Object>>> toSse(Flux<ModelStreamEvent> events) {
		return events.concatMap(ModelStreamSse::toSse)
			.onErrorResume(error -> Flux.just(errorEvent(error)));
	}

	private static Flux<ServerSentEvent<Map<String, Object>>> toSse(ModelStreamEvent event) {
		return switch (event) {
			case Token token -> Flux.just(sse("token", Map.of("text", token.text())));
			case Thinking thinking -> Flux.just(sse("thinking", Map.of("text", thinking.text())));
			case Completed completed -> Flux.just(usage(completed.usage()),
					sse("done", Map.of("finishReason", completed.finishReason() == FinishReason.LENGTH ? "length"
							: "stop")));
		};
	}

	private static ServerSentEvent<Map<String, Object>> usage(ModelStreamEvent.Usage usage) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("promptTokens", usage.promptTokens());
		data.put("completionTokens", usage.completionTokens());
		data.put("tokensPerSecond", usage.tokensPerSecond());
		data.put("timeToFirstTokenMs", usage.timeToFirstTokenMs());
		data.put("totalDurationMs", usage.totalDurationMs());
		return sse("usage", data);
	}

	static ServerSentEvent<Map<String, Object>> errorEvent(Throwable error) {
		ModelException e = error instanceof ModelException me ? me
				: new ModelException(ErrorCode.INTERNAL_ERROR, "Erro inesperado ao gerar a resposta.", false, error);
		return sse("error", Map.of("code", e.getCode().name(), "message", String.valueOf(e.getMessage()),
				"retryable", e.isRetryable()));
	}

	static ServerSentEvent<Map<String, Object>> sse(String type, Map<String, Object> fields) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", type);
		data.putAll(fields);
		return ServerSentEvent.<Map<String, Object>>builder(data).event(type).build();
	}
}
