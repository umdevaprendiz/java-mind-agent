package com.example.java_mind_agent.models.ollama;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.observation.ObservationRegistry;

/**
 * Um {@link ChatClient} por servidor Ollama. O padrão ({@code spring.ai.ollama.base-url}) é o que o Spring AI já
 * configura; os outros, que algum tier aponta com {@code base-url} (ex.: o Extra em outro computador com GPU), são
 * montados com as mesmas peças — mesmos clientes HTTP, ferramentas, observação e política de repetição — e guardados.
 *
 * <p>Nenhum deles baixa modelo sozinho: baixar é decisão do usuário (F07).
 */
@Component
class OllamaChatClients implements OllamaChatService.ChatClientSource {

	private final ChatClient defaultClient;

	private final OllamaHosts hosts;

	private final ObjectProvider<RestClient.Builder> restClientBuilder;

	private final ObjectProvider<WebClient.Builder> webClientBuilder;

	private final ToolCallingManager toolCallingManager;

	private final ObjectProvider<ObservationRegistry> observationRegistry;

	private final ObjectProvider<RetryTemplate> retryTemplate;

	private final Map<String, ChatClient> remote = new ConcurrentHashMap<>();

	OllamaChatClients(ChatClient.Builder defaultBuilder, OllamaHosts hosts,
			ObjectProvider<RestClient.Builder> restClientBuilder, ObjectProvider<WebClient.Builder> webClientBuilder,
			ToolCallingManager toolCallingManager, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<RetryTemplate> retryTemplate) {
		this.defaultClient = defaultBuilder.build();
		this.hosts = hosts;
		this.restClientBuilder = restClientBuilder;
		this.webClientBuilder = webClientBuilder;
		this.toolCallingManager = toolCallingManager;
		this.observationRegistry = observationRegistry;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatClient forHost(@Nullable String baseUrl) {
		String host = hosts.resolve(baseUrl);
		return hosts.isDefault(host) ? defaultClient : remote.computeIfAbsent(host, this::build);
	}

	private ChatClient build(String host) {
		OllamaApi api = OllamaApi.builder()
			.baseUrl(host)
			.restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder))
			.webClientBuilder(webClientBuilder.getIfAvailable(WebClient::builder))
			.responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
			.build();
		OllamaChatModel model = OllamaChatModel.builder()
			.ollamaApi(api)
			.options(OllamaChatOptions.builder().build())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.modelManagementOptions(ModelManagementOptions.defaults())
			.retryTemplate(retryTemplate.getIfUnique(() -> RetryUtils.DEFAULT_RETRY_TEMPLATE))
			.build();
		return ChatClient.builder(model).build();
	}
}
