package com.example.java_mind_agent.models;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.java_mind_agent.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * Perguntas rápidas a um Ollama (quais modelos tem, quais estão na memória, o que cada um sabe fazer). Cada instância
 * fala com um host. Tempos curtos de propósito: quem chama é o catálogo da tela, que não pode travar porque um host
 * remoto sumiu. Todo problema de rede ou HTTP vira {@link ModelException}.
 *
 * <p>O download e a remoção de modelos (F07) são outra coisa: streaming longo, sem esse tempo limite.
 */
public class OllamaAdminClient {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

	private final String baseUrl;

	private final WebClient web;

	OllamaAdminClient(String baseUrl) {
		this.baseUrl = baseUrl;
		HttpClient http = HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
			.responseTimeout(READ_TIMEOUT);
		this.web = WebClient.builder().baseUrl(baseUrl).clientConnector(new ReactorClientHttpConnector(http)).build();
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
