package com.example.java_mind_agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import com.example.java_mind_agent.agent.AgentEvent.FinishReason;
import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.support.OpenApiContract;

import tools.jackson.databind.json.JsonMapper;

/** Cada evento do agente, traduzido, passa na validação do schema do contrato ({@code ChatStreamEvent}). */
class AgentSseTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void todosOsEventosTraduzidosCumpremOContratoDoChat() {
		List<AgentEvent> eventos = List.of(
				new AgentEvent.Status(Phase.LOADING_MODEL, "Carregando o modelo na memória"),
				new AgentEvent.Thinking("hmm"),
				new AgentEvent.Token("Um "),
				new AgentEvent.ToolCall("call_1", "search_docs", Map.of("query", "records", "javaVersion", 21)),
				new AgentEvent.ToolResult("call_1", "search_docs", true, "3 trechos", "…", 85),
				new AgentEvent.ToolResult("call_2", "run_java", false, "falhou", null, 0),
				new AgentEvent.Usage(312, 148, 6.4, 2300L, 25400),
				new AgentEvent.Usage(1, 1, null, null, 0));

		for (AgentEvent evento : eventos) {
			ServerSentEvent<Map<String, Object>> sse = AgentSse.toSse(evento);

			assertThat(sse.event()).isEqualTo(sse.data().get("type"));
			assertThat(OpenApiContract.valida("ChatStreamEvent", JSON.writeValueAsString(sse.data())))
				.as("evento %s", evento)
				.isEmpty();
		}
	}

	@Test
	void fimViraDoneComOMotivoDoContrato() {
		assertThat(AgentSse.toSse(new AgentEvent.Finished(FinishReason.STOP, null, 1, 0)).data())
			.isEqualTo(Map.of("type", "done", "finishReason", "stop"));
		assertThat(AgentSse.toSse(new AgentEvent.Finished(FinishReason.CANCELLED, null, 1, 0)).data())
			.containsEntry("finishReason", "cancelled");
		assertThat(AgentSse.toSse(new AgentEvent.Finished(FinishReason.LENGTH, AgentEvent.Limit.TIME, 3, 2)).data())
			.containsEntry("finishReason", "length");
	}
}
