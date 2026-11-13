package com.example.java_mind_agent.models;

import java.util.ArrayList;
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
 * </pre>
 */
@RestController
@ConditionalOnProperty(prefix = "app.models.debug", name = "endpoint-enabled", havingValue = "true")
class OllamaStreamController {

	private final OllamaChatService chat;

	OllamaStreamController(OllamaChatService chat) {
		this.chat = chat;
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

		return SseSupport.withKeepAlive(ModelStreamSse.toSse(chat.stream(request.model(), messages, options)));
	}

	/**
	 * @param model   nome do modelo no Ollama (ex.: {@code qwen2.5:1.5b})
	 * @param prompt  a pergunta
	 * @param system  instrução de sistema opcional
	 */
	record StreamRequest(
			@NotBlank String model,
			@NotBlank String prompt,
			String system,
			Double temperature,
			Integer numCtx,
			Integer numPredict,
			String keepAlive,
			Boolean think) {
	}
}
