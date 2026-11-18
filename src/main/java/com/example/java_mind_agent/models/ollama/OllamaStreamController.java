package com.example.java_mind_agent.models.ollama;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.java_mind_agent.common.sse.SseSupport;
import com.example.java_mind_agent.models.routing.ModelRouter;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.models.Tier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;

/**
 * Endpoint <b>interno</b> da F05: devolve, em SSE, o stream de tokens de qualquer modelo instalado no Ollama.
 * Serve para testar modelos e medir velocidade sem passar pelo agente. Fica fora de {@code /api} (não faz parte do
 * contrato) e só existe com {@code app.models.debug.endpoint-enabled=true}, ligado apenas no perfil dev.
 *
 * <pre>
 * curl -N -X POST localhost:8080/internal/models/stream -H 'Content-Type: application/json' \
 *      -d '{"model":"qwen2.5:1.5b","prompt":"O que é um record em Java?"}'
 *
 * # pelo roteador (F08): "tier" escolhe o nível; sem "model" e sem "tier", vale o padrão
 * curl -N -X POST localhost:8080/internal/models/stream -H 'Content-Type: application/json' \
 *      -d '{"tier":"low","prompt":"O que é um record em Java?"}'
 * </pre>
 */
@RestController
@ConditionalOnProperty(prefix = "app.models.debug", name = "endpoint-enabled", havingValue = "true")
class OllamaStreamController {

	private final OllamaChatService chat;

	private final ModelRouter router;

	OllamaStreamController(OllamaChatService chat, ModelRouter router) {
		this.chat = chat;
		this.router = router;
	}

	@PostMapping(path = "/internal/models/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<Map<String, Object>>> stream(@Valid @RequestBody StreamRequest request) {
		List<Message> messages = new ArrayList<>(2);
		if (request.system() != null && !request.system().isBlank()) {
			messages.add(new SystemMessage(request.system()));
		}
		messages.add(new UserMessage(request.prompt()));
		GenerationOptions options = new GenerationOptions(request.temperature(), request.numCtx(),
				request.numPredict(), request.keepAlive(), request.think(), null, null, null);

		if (request.model() != null && !request.model().isBlank()) {
			return SseSupport.withKeepAlive(ModelStreamSse.toSse(chat.stream(request.model(), messages, options)));
		}
		// Sem "model": o roteador escolhe pelo tier (ou o padrão). Falhas conhecidas saem como problem+json, antes do stream.
		Route route = router.resolve(request.tier());
		Map<String, Object> routed = new LinkedHashMap<>();
		routed.put("tier", route.tier().value());
		routed.put("label", route.label());
		routed.put("model", route.model());
		routed.put("host", route.host());
		routed.put("remote", route.remote());
		routed.put("loaded", route.loaded());
		routed.put("fromDefault", route.fromDefault());
		return SseSupport.withKeepAlive(Flux.concat(Flux.just(ModelStreamSse.sse("route", routed)),
				ModelStreamSse.toSse(router.stream(route, messages, options))));
	}

	/**
	 * @param model   nome do modelo no Ollama (ex.: {@code qwen2.5:1.5b}); sem ele, vale o {@code tier}
	 * @param tier    {@code low}, {@code medium}, {@code strong} ou {@code extra}; sem ele e sem {@code model}, o padrão
	 * @param prompt  a pergunta
	 * @param system  instrução de sistema opcional
	 */
	record StreamRequest(
			String model,
			Tier tier,
			@NotBlank String prompt,
			String system,
			Double temperature,
			Integer numCtx,
			Integer numPredict,
			String keepAlive,
			Boolean think) {
	}
}
