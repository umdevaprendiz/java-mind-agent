package com.example.java_mind_agent.models.ollama;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ModelsProperties;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.FinishReason;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Thinking;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Token;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCallRequest;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCalls;
import com.example.java_mind_agent.models.routing.ModelRouter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gera respostas com qualquer modelo local do Ollama, em streaming, pelo {@link ChatClient} do Spring AI.
 *
 * <ul>
 * <li><b>Opções por requisição</b>: modelo, temperatura, janela de contexto, {@code keep_alive}, raciocínio.</li>
 * <li><b>Cancelamento</b>: cancelar a assinatura fecha a conexão com o Ollama, que para de gerar.</li>
 * <li><b>Tempo</b>: limite para o primeiro pedaço (carregar o modelo na CPU é lento), para o silêncio entre
 * pedaços e para a duração total.</li>
 * <li><b>Erros</b>: viram {@link ModelException} com código do contrato (Ollama fora do ar, modelo ausente, falta de
 * memória, tempo esgotado).</li>
 * <li><b>Métricas</b>: tempo até o primeiro token e tokens por segundo, no {@link Completed} final.</li>
 * <li><b>Ferramentas</b>: se o pedido as oferece, o modelo pode pedir para chamá-las ({@link ToolCalls}); este
 * serviço só relata o pedido. Quem executa e repete a geração é o agente, que assim controla passos, limites e
 * cancelamento.</li>
 * </ul>
 *
 * O serviço não escolhe modelo nem faz fallback: quem chama ({@link ModelRouter}, por tier) decide qual modelo e
 * qual servidor usar.
 */
@Service
public class OllamaChatService {

	private static final Logger log = LoggerFactory.getLogger(OllamaChatService.class);

	// Chaves de metadados preenchidas pelo OllamaChatModel do Spring AI.
	private static final String THINKING = "thinking";

	private static final String DONE = "done";

	private static final String EVAL_DURATION = "eval-duration";

	private static final String LOAD_DURATION = "load-duration";

	/**
	 * O ChatClient do Spring AI 2 traz um {@link ToolCallingAdvisor} que executa sozinho as ferramentas que o modelo
	 * pede. Este o substitui por um que nunca executa: o pedido chega ao agente como {@link ToolCalls}.
	 */
	private static final ToolCallingAdvisor REPORT_ONLY_TOOLS = ToolCallingAdvisor.builder()
		.toolExecutionEligibilityChecker(response -> false)
		.build();

	/** De onde vem o cliente de chat de cada servidor Ollama; {@code null} = o padrão. */
	@FunctionalInterface
	interface ChatClientSource {

		ChatClient forHost(@Nullable String baseUrl);

	}

	private final ChatClientSource clients;

	private final ModelsProperties.Generation defaults;

	@Autowired
	OllamaChatService(OllamaChatClients clients, ModelsProperties properties) {
		this.clients = clients;
		this.defaults = properties.generation();
	}

	/** Um só Ollama para qualquer host: o que os testes de geração precisam. */
	OllamaChatService(ChatClient.Builder chatClientBuilder, ModelsProperties properties) {
		ChatClient chatClient = chatClientBuilder.build();
		this.clients = host -> chatClient;
		this.defaults = properties.generation();
	}

	/** Gera a resposta no Ollama padrão. */
	public Flux<ModelStreamEvent> stream(String model, List<Message> messages, GenerationOptions options) {
		return stream(null, model, messages, options);
	}

	/**
	 * Gera a resposta de {@code model} para {@code messages}, no Ollama de {@code host} (o de um tier pode ser outro
	 * computador; {@code null} = o padrão).
	 *
	 * @return {@link Token}s e {@link Thinking}s à medida que chegam, e um {@link Completed} no fim; ou erro
	 *         {@link ModelException}. Nada acontece até alguém assinar o {@link Flux}.
	 */
	public Flux<ModelStreamEvent> stream(@Nullable String host, String model, List<Message> messages,
			GenerationOptions options) {
		return stream(host, model, messages, options, List.of());
	}

	/**
	 * Como {@link #stream(String, String, List, GenerationOptions)}, oferecendo {@code tools} ao modelo. Se ele pedir
	 * alguma, o fluxo traz um {@link ToolCalls} antes do {@link Completed}; nenhuma é executada aqui. Com a lista
	 * vazia, o modelo nem fica sabendo de ferramentas.
	 */
	public Flux<ModelStreamEvent> stream(@Nullable String host, String model, List<Message> messages,
			GenerationOptions options, List<ToolCallback> tools) {
		Assert.hasText(model, "model é obrigatório");
		Assert.notEmpty(messages, "messages é obrigatório");
		Assert.notNull(options, "options é obrigatório");
		Assert.notNull(tools, "tools é obrigatório");

		OllamaChatOptions.Builder chatOptions = chatOptions(model, options);
		if (!tools.isEmpty()) {
			chatOptions.toolCallbacks(tools);
		}
		Duration firstTokenTimeout = pick(options.firstTokenTimeout(), defaults.firstTokenTimeout());
		Duration idleTimeout = pick(options.idleTimeout(), defaults.idleTimeout());
		Duration maxDuration = pick(options.maxDuration(), defaults.maxDuration());

		return Flux.defer(() -> {
			Run run = new Run();
			ChatClient.ChatClientRequestSpec request = clients.forHost(host)
				.prompt()
				.messages(messages)
				.options(chatOptions);
			if (!tools.isEmpty()) {
				request = request.advisors(REPORT_ONLY_TOOLS);
			}
			Flux<ChatResponse> responses = request.stream().chatResponse();
			return withTimeouts(responses, firstTokenTimeout, idleTimeout, maxDuration)
				.concatMap(run::events)
				.concatWith(Flux.defer(() -> run.ending(model)));
		}).onErrorMap(error -> OllamaErrorMapper.map(model, error));
	}

	private OllamaChatOptions.Builder chatOptions(String model, GenerationOptions options) {
		OllamaChatOptions.Builder builder = OllamaChatOptions.builder()
			.model(model)
			.temperature(options.temperature() != null ? options.temperature() : defaults.temperature())
			.numCtx(options.numCtx() != null ? options.numCtx() : defaults.numCtx())
			.keepAlive(options.keepAlive() != null ? options.keepAlive() : defaults.keepAlive());
		if (options.numPredict() != null) {
			builder.numPredict(options.numPredict());
		}
		if (options.think() != null) {
			if (options.think()) {
				builder.enableThinking();
			}
			else {
				builder.disableThinking();
			}
		}
		return builder;
	}

	private static Duration pick(@Nullable Duration requested, Duration fallback) {
		return requested != null ? requested : fallback;
	}

	private static Flux<ChatResponse> withTimeouts(Flux<ChatResponse> responses, Duration firstTokenTimeout,
			Duration idleTimeout, Duration maxDuration) {
		Flux<ChatResponse> guarded = responses;
		if (isPositive(firstTokenTimeout) || isPositive(idleTimeout)) {
			guarded = guarded.timeout(
					isPositive(firstTokenTimeout) ? Mono.delay(firstTokenTimeout) : Mono.<Long>never(),
					chunk -> isPositive(idleTimeout) ? Mono.delay(idleTimeout) : Mono.<Long>never());
		}
		if (isPositive(maxDuration)) {
			guarded = guarded.takeUntilOther(Mono.delay(maxDuration)
				.flatMap(tick -> Mono.<Long>error(new TimeoutException("duração máxima da geração atingida"))));
		}
		return guarded;
	}

	private static boolean isPositive(Duration duration) {
		return !duration.isZero() && !duration.isNegative();
	}

	/** Estado de uma geração: marca os instantes e guarda o último pedaço, que traz as métricas do Ollama. */
	private static final class Run {

		private final long startedAt = System.nanoTime();

		private @Nullable Long firstOutputAt;

		private @Nullable ChatResponse finalResponse;

		private @Nullable String doneReason;

		private final List<ToolCallRequest> toolCalls = new ArrayList<>();

		Flux<ModelStreamEvent> events(ChatResponse response) {
			List<ModelStreamEvent> events = new ArrayList<>(2);
			Generation generation = response.getResult();
			if (generation != null) {
				AssistantMessage output = generation.getOutput();
				if (output.getMetadata().get(THINKING) instanceof String thinking && !thinking.isEmpty()) {
					markFirstOutput();
					events.add(new Thinking(thinking));
				}
				String text = output.getText();
				if (text != null && !text.isEmpty()) {
					markFirstOutput();
					events.add(new Token(text));
				}
				for (AssistantMessage.ToolCall call : output.getToolCalls()) {
					markFirstOutput();
					toolCalls.add(new ToolCallRequest(call.name(), call.arguments()));
				}
				if (generation.getMetadata().getFinishReason() != null) {
					doneReason = generation.getMetadata().getFinishReason();
				}
			}
			Object done = response.getMetadata().get(DONE);
			if (Boolean.TRUE.equals(done)) {
				finalResponse = response;
			}
			return Flux.fromIterable(events);
		}

		/** O que fecha a geração: os pedidos de ferramenta, se houve, e o {@link Completed}. */
		Flux<ModelStreamEvent> ending(String model) {
			Mono<ModelStreamEvent> completed = completion(model);
			return toolCalls.isEmpty() ? completed.flux()
					: Flux.concat(Mono.just(new ToolCalls(List.copyOf(toolCalls))), completed);
		}

		private Mono<ModelStreamEvent> completion(String model) {
			ChatResponse last = finalResponse;
			if (last == null) {
				return Mono.error(new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
						"A resposta do modelo '" + model + "' foi interrompida antes de terminar.", true));
			}
			long totalMs = millisSince(startedAt);
			ChatResponseMetadata metadata = last.getMetadata();
			Usage usage = metadata.getUsage();
			int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
			int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
			Duration evalDuration = metadata.get(EVAL_DURATION);
			Duration loadDuration = metadata.get(LOAD_DURATION);

			Double tokensPerSecond = evalDuration != null && evalDuration.toNanos() > 0 && completionTokens > 0
					? completionTokens / (evalDuration.toNanos() / 1_000_000_000.0) : null;
			Long timeToFirstTokenMs = firstOutputAt != null ? (firstOutputAt - startedAt) / 1_000_000 : null;
			Long loadMs = loadDuration != null ? loadDuration.toMillis() : null;

			log.info("{}: {} tokens gerados, {} tok/s, primeiro token em {} ms, carga {} ms, total {} ms", model,
					completionTokens, tokensPerSecond != null ? "%.1f".formatted(tokensPerSecond) : "?",
					timeToFirstTokenMs, loadMs, totalMs);

			FinishReason reason = "length".equalsIgnoreCase(doneReason) ? FinishReason.LENGTH : FinishReason.STOP;
			return Mono.just(new Completed(model, reason, new ModelStreamEvent.Usage(promptTokens, completionTokens,
					tokensPerSecond, timeToFirstTokenMs, loadMs, totalMs)));
		}

		private void markFirstOutput() {
			if (firstOutputAt == null) {
				firstOutputAt = System.nanoTime();
			}
		}

		private static long millisSince(long nanoTime) {
			return (System.nanoTime() - nanoTime) / 1_000_000;
		}
	}
}
