package com.example.java_mind_agent.models.manager;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.java_mind_agent.common.sse.SseSupport;

import reactor.core.publisher.Flux;

/**
 * Gerenciador de modelos: baixar (com progresso), remover, carregar na memória e descarregar. O {@code modelId} é
 * {@code low}, {@code medium}, {@code strong}, {@code extra} ou {@code embedding}; a regra de cada operação está em
 * {@link ModelManagerService}. Erros anteriores ao stream saem como {@code problem+json} (ver {@code
 * ApiExceptionHandler}).
 */
@RestController
@RequestMapping("/api/models")
class ModelManagerController {

	private final ModelManagerService manager;

	ModelManagerController(ModelManagerService manager) {
		this.manager = manager;
	}

	/**
	 * SSE com {@code progress} (várias), e {@code done} no fim ou {@code error}. Fechar a conexão cancela o download.
	 * O keep-alive cobre as etapas sem progresso (ler o manifesto, verificar os 9 GB baixados).
	 */
	@PostMapping(path = "/{modelId}/pull", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<Map<String, Object>>> pull(@PathVariable String modelId) {
		return SseSupport.withKeepAlive(ModelPullSse.toSse(manager.pull(modelId)));
	}

	@DeleteMapping("/{modelId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String modelId) {
		manager.delete(modelId);
	}

	/** Responde na hora; o carregamento segue em segundo plano e aparece como {@code loaded} em {@code GET /api/models}. */
	@PostMapping("/{modelId}/warmup")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void warmUp(@PathVariable String modelId) {
		manager.warmUp(modelId);
	}

	@PostMapping("/{modelId}/unload")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unload(@PathVariable String modelId) {
		manager.unload(modelId);
	}
}
