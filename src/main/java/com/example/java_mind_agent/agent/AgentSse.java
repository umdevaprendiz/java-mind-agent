package com.example.java_mind_agent.agent;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.codec.ServerSentEvent;

import com.example.java_mind_agent.models.ollama.ModelStreamSse;

/**
 * Traduz {@link AgentEvent} para os eventos SSE do contrato ({@code api/openapi.yaml}): o nome em {@code event:} é
 * igual ao campo {@code type} do JSON em {@code data:}. O chat (F22) acrescenta {@code start}, {@code done} com os ids
 * das mensagens e o {@code error}; aqui só há o que o agente conta por conta própria.
 */
public final class AgentSse {

	private AgentSse() {
	}

	/** O evento SSE de {@code event}; {@link AgentEvent.Finished} vira {@code done} só com o {@code finishReason}. */
	public static ServerSentEvent<Map<String, Object>> toSse(AgentEvent event) {
		return switch (event) {
			case AgentEvent.Status status -> ModelStreamSse.sse("status",
					Map.of("phase", status.phase().value(), "message", status.message()));
			case AgentEvent.Thinking thinking -> ModelStreamSse.sse("thinking", Map.of("text", thinking.text()));
			case AgentEvent.Token token -> ModelStreamSse.sse("token", Map.of("text", token.text()));
			case AgentEvent.ToolCall call -> ModelStreamSse.sse("tool_call",
					Map.of("callId", call.callId(), "tool", call.tool(), "arguments", call.arguments()));
			case AgentEvent.ToolResult result -> toolResult(result);
			case AgentEvent.Usage usage -> usage(usage);
			case AgentEvent.Finished finished -> ModelStreamSse.sse("done",
					Map.of("finishReason", finished.reason().value()));
		};
	}

	private static ServerSentEvent<Map<String, Object>> toolResult(AgentEvent.ToolResult result) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("callId", result.callId());
		fields.put("tool", result.tool());
		fields.put("ok", result.ok());
		fields.put("summary", result.summary());
		fields.put("output", result.output());
		fields.put("durationMs", result.durationMs());
		return ModelStreamSse.sse("tool_result", fields);
	}

	private static ServerSentEvent<Map<String, Object>> usage(AgentEvent.Usage usage) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("promptTokens", usage.promptTokens());
		fields.put("completionTokens", usage.completionTokens());
		fields.put("tokensPerSecond", usage.tokensPerSecond());
		fields.put("timeToFirstTokenMs", usage.timeToFirstTokenMs());
		fields.put("totalDurationMs", usage.totalDurationMs());
		return ModelStreamSse.sse("usage", fields);
	}
}
