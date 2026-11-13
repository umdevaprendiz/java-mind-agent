package com.example.java_mind_agent.models;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.java_mind_agent.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conversa com um Ollama sobre os <i>modelos</i> (não sobre as respostas, que é o {@link OllamaChatService}). Cada
 * instância fala com um host.
 *
 * <ul>
 * <li><b>Perguntas rápidas</b> (quais modelos tem, quais estão na memória, o que cada um sabe fazer): tempos curtos de
 * propósito, porque quem chama é o catálogo da tela, que não pode travar porque um host remoto sumiu.</li>
 * <li><b>Operações longas</b> (baixar, carregar na memória, descarregar, remover): sem limite de resposta, só de
 * silêncio ou um teto explícito, porque baixar 9 GB ou carregar um 7B em CPU leva minutos.</li>
 * </ul>
 *
 * Todo problema de rede ou HTTP vira {@link ModelException}.
 */
public class OllamaAdminClient {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

	/** Teto de operações curtas que não cabem nos {@link #READ_TIMEOUT}: remover e descarregar um modelo. */
	static final Duration MANAGE_TIMEOUT = Duration.ofSeconds(30);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final String baseUrl;

	/** Perguntas rápidas: resposta em até {@link #READ_TIMEOUT}. */
	private final WebClient web;

	/** Operações longas: mesmo servidor, sem tempo limite de resposta (cada chamada põe o seu). */
	private final WebClient streaming;

	OllamaAdminClient(String baseUrl) {
		this.baseUrl = baseUrl;
		HttpClient http = HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
			.responseTimeout(READ_TIMEOUT);
		this.web = WebClient.builder().baseUrl(baseUrl).clientConnector(new ReactorClientHttpConnector(http)).build();
		HttpClient longHttp = HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis());
		this.streaming = WebClient.builder()
			.baseUrl(baseUrl)
			.clientConnector(new ReactorClientHttpConnector(longHttp))
			.build();
	}

	public String baseUrl() {
		return baseUrl;
	}

	/** Modelos baixados neste host ({@code GET /api/tags}). */
	public List<InstalledModel> listInstalled() {
		TagsResponse response = get("/api/tags", TagsResponse.class);
		return response.models() == null ? List.of() : response.models();
	}

	/** Modelos carregados na memória agora ({@code GET /api/ps}). */
	public List<LoadedModel> listLoaded() {
		PsResponse response = get("/api/ps", PsResponse.class);
		return response.models() == null ? List.of() : response.models();
	}

	/**
	 * O que o modelo sabe fazer ({@code POST /api/show}): {@code completion}, {@code tools}, {@code thinking},
	 * {@code vision}, {@code embedding}... Lista vazia em versões do Ollama que ainda não informam isso.
	 *
	 * @throws ModelException {@code MODEL_NOT_INSTALLED} se o host não tem o modelo
	 */
	public List<String> capabilities(String model) {
		try {
			ShowResponse response = web.post()
				.uri("/api/show")
				.bodyValue(Map.of("model", model))
				.retrieve()
				.bodyToMono(ShowResponse.class)
				.block(READ_TIMEOUT.plusSeconds(1));
			return response == null || response.capabilities() == null ? List.of() : response.capabilities();
		}
		catch (RuntimeException e) {
			throw OllamaErrorMapper.map(model, e);
		}
	}

	/** Versão do servidor ({@code GET /api/version}); serve também para saber se o host está de pé. */
	public String version() {
		VersionResponse response = get("/api/version", VersionResponse.class);
		return response.version() == null ? "" : response.version();
	}

	/**
	 * Baixa um modelo ({@code POST /api/pull}) e devolve as linhas de progresso do Ollama como chegam. Cancelar a
	 * assinatura fecha a conexão e o Ollama interrompe o download, <b>guardando o que já baixou</b>: baixar de novo
	 * continua de onde parou.
	 *
	 * <p>Uma linha {@code {"error": "..."}} no meio do stream é entregue como item ({@link PullLine#error()}); quem
	 * consome decide o que fazer. Falha de conexão ou HTTP chega como erro do {@code Flux}, e {@code idleTimeout} sem
	 * nenhuma linha vira {@link java.util.concurrent.TimeoutException}.
	 */
	public Flux<PullLine> pull(String model, Duration idleTimeout) {
		return streaming.post()
			.uri("/api/pull")
			.bodyValue(Map.of("model", model, "stream", true))
			.retrieve()
			.bodyToFlux(String.class)
			.map(String::strip)
			.filter(line -> !line.isEmpty())
			.map(OllamaAdminClient::parsePullLine)
			.timeout(idleTimeout);
	}

	/** Remove o modelo do disco ({@code DELETE /api/delete}). */
	public void delete(String model) {
		try {
			streaming.method(HttpMethod.DELETE)
				.uri("/api/delete")
				.bodyValue(Map.of("model", model))
				.retrieve()
				.toBodilessEntity()
				.timeout(MANAGE_TIMEOUT)
				.block();
		}
		catch (RuntimeException e) {
			throw OllamaErrorMapper.map(model, e);
		}
	}

	/**
	 * Carrega o modelo na memória e o mantém lá por {@code keepAlive} (uma geração ou embedding vazios). Modelo de
	 * embedding não aceita {@code /api/generate}, por isso usa {@code /api/embed}.
	 */
	public void load(String model, boolean embedding, String keepAlive, Duration timeout) {
		keepAlive(model, embedding, keepAlive, timeout);
	}

	/** Tira o modelo da memória na hora ({@code keep_alive = 0}). */
	public void unload(String model, boolean embedding) {
		keepAlive(model, embedding, 0, MANAGE_TIMEOUT);
	}

	private void keepAlive(String model, boolean embedding, Object keepAlive, Duration timeout) {
		Map<String, Object> body = embedding
				? Map.of("model", model, "input", "", "keep_alive", keepAlive)
				: Map.of("model", model, "keep_alive", keepAlive, "stream", false);
		try {
			streaming.post()
				.uri(embedding ? "/api/embed" : "/api/generate")
				.bodyValue(body)
				.retrieve()
				.toBodilessEntity()
				.timeout(timeout)
				.block();
		}
		catch (RuntimeException e) {
			throw OllamaErrorMapper.map(model, e);
		}
	}

	private static PullLine parsePullLine(String line) {
		try {
			return JSON.readValue(line, PullLine.class);
		}
		catch (RuntimeException e) {
			throw new ModelException(ErrorCode.INTERNAL_ERROR,
					"O Ollama mandou uma linha de progresso que não entendi: " + line, false, e);
		}
	}

	private <T> T get(String path, Class<T> type) {
		try {
			T body = web.get().uri(path).retrieve().bodyToMono(type).block(READ_TIMEOUT.plusSeconds(1));
			if (body == null) {
				throw new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
						"O Ollama em " + baseUrl + " respondeu vazio a " + path + ".", true);
			}
			return body;
		}
		catch (ModelException e) {
			throw e;
		}
		catch (WebClientResponseException e) {
			// Erro HTTP numa pergunta sobre o servidor (não sobre um modelo): é o servidor que não serve, não um
			// modelo que falta. Um 404 aqui costuma ser outro programa respondendo naquela porta.
			throw new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
					"O servidor em " + baseUrl + " respondeu HTTP " + e.getStatusCode().value() + " a " + path
							+ ". Confira se é mesmo um Ollama.",
					e.getStatusCode().is5xxServerError(), e);
		}
		catch (RuntimeException e) {
			throw OllamaErrorMapper.map(baseUrl, e);
		}
	}

	/** Item de {@code /api/tags}: um modelo baixado, com o tamanho em disco em bytes. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record InstalledModel(String name, long size, @Nullable String digest) {
	}

	/** Item de {@code /api/ps}: um modelo na memória; {@code sizeVram} &gt; 0 indica que parte está na GPU. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LoadedModel(String name, long size, @JsonProperty("size_vram") long sizeVram) {
	}

	/**
	 * Uma linha do {@code /api/pull}: {@code {"status":"pulling manifest"}}, depois uma por camada com
	 * {@code digest}/{@code total}/{@code completed} em bytes, e por fim {@code "success"}. Se algo der errado vem
	 * {@code {"error":"..."}}.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PullLine(@Nullable String status, @Nullable String digest, @Nullable Long total,
			@Nullable Long completed, @Nullable String error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TagsResponse(@Nullable List<InstalledModel> models) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PsResponse(@Nullable List<LoadedModel> models) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ShowResponse(@Nullable List<String> capabilities) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record VersionResponse(@Nullable String version) {
	}
}
