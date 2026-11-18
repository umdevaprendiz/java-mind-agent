package com.example.java_mind_agent.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.java_mind_agent.common.sse.SseSupport;
import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.ollama.ModelStreamSse;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;

/**
 * Endpoint <b>interno</b> da F15: faz uma pergunta ao agente e devolve, em SSE, tudo que ele conta (status, tokens,
 * ferramentas, uso). Serve para testar o agente antes de existir o chat (F22). Fica fora de {@code /api} (não faz
 * parte do contrato) e só existe com {@code app.agent.debug.endpoint-enabled=true}, ligado apenas no perfil dev, que
 * também liga duas ferramentas de demonstração.
 *
 * <pre>
 * curl -N -X POST localhost:8080/internal/agent/run -H 'Content-Type: application/json' \
 *      -d '{"message":"O que mudou no Java 17 em relação ao Java 11?","tier":"low"}'
 * </pre>
 *
 * O primeiro evento é {@code route} (modelo, versão do Java assumida, se há ferramentas) e o último, {@code summary}
 * (passos, ferramentas usadas e limite que cortou, se algum); entre eles, os eventos do contrato do chat.
 */
@RestController
@ConditionalOnProperty(prefix = "app.agent.debug", name = "endpoint-enabled", havingValue = "true")
class AgentDebugController {

	private final AgentService agent;

	private final AgentProperties properties;

	AgentDebugController(AgentService agent, AgentProperties properties) {
		this.agent = agent;
		this.properties = properties;
	}

	@PostMapping(path = "/internal/agent/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<Map<String, Object>>> run(@Valid @RequestBody RunRequest request) {
		AgentRun run = agent.prepare(request.toAgentRequest(properties.limits()));
		Flux<ServerSentEvent<Map<String, Object>>> events = run.events()
			.concatMap(event -> event instanceof AgentEvent.Finished finished
					? Flux.just(AgentSse.toSse(finished), summary(finished))
					: Flux.just(AgentSse.toSse(event)))
			.onErrorResume(error -> Flux.just(ModelStreamSse.errorEvent(error)));
		return SseSupport.withKeepAlive(Flux.concat(Flux.just(route(run)), events));
	}

	private static ServerSentEvent<Map<String, Object>> route(AgentRun run) {
		Route route = run.route();
		AssumedVersion version = run.javaVersion();
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("tier", route.tier().value());
		fields.put("model", route.model());
		fields.put("loaded", route.loaded());
		fields.put("supportsTools", route.supportsTools());
		fields.put("toolsEnabled", run.toolsEnabled());
		fields.put("javaVersion", version.version());
		fields.put("javaVersionSource", version.source().name());
		return ModelStreamSse.sse("route", fields);
	}

	private static ServerSentEvent<Map<String, Object>> summary(AgentEvent.Finished finished) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("steps", finished.steps());
		fields.put("toolCalls", finished.toolCalls());
		fields.put("limit", finished.limit() == null ? null : finished.limit().name());
		return ModelStreamSse.sse("summary", fields);
	}

	/**
	 * @param message     a pergunta
	 * @param history     mensagens anteriores, para testar conversa (papéis {@code user} e {@code assistant})
	 * @param tier        {@code low}, {@code medium}, {@code strong} ou {@code extra}; sem ele, o padrão
	 * @param javaVersion versão do Java fixada pela pessoa
	 * @param language    {@code pt-BR} ou {@code en}
	 * @param maxSteps    limite de passos desta pergunta (o restante vem de {@code app.agent.limits})
	 * @param maxTokens   limite de tokens gerados desta pergunta
	 * @param think       liga/desliga o raciocínio do modelo
	 */
	record RunRequest(
			@NotBlank String message,
			@Nullable List<Turn> history,
			@Nullable Tier tier,
			@Nullable Integer javaVersion,
			@Nullable Language language,
			@Nullable Integer maxSteps,
			@Nullable Integer maxTokens,
			@Nullable Boolean think) {

		AgentRequest toAgentRequest(AgentLimits defaults) {
			List<Message> messages = new ArrayList<>();
			if (history != null) {
				for (Turn turn : history) {
					messages.add("assistant".equalsIgnoreCase(turn.role()) ? new AssistantMessage(turn.content())
							: new UserMessage(turn.content()));
				}
			}
			AgentLimits limits = new AgentLimits(maxSteps != null ? maxSteps : defaults.maxSteps(),
					defaults.maxToolCalls(), maxTokens != null ? maxTokens : defaults.maxTokens(),
					defaults.maxDuration(), defaults.toolTimeout(), defaults.maxToolOutputChars());
			GenerationOptions generation = think == null ? null
					: new GenerationOptions(null, null, null, null, think, null, null, null);
			return new AgentRequest(message, messages, tier, javaVersion, language, null, limits, generation);
		}
	}

	record Turn(String role, String content) {
	}
}
