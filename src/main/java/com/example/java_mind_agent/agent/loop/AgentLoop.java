package com.example.java_mind_agent.agent.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.agent.AgentEvent;
import com.example.java_mind_agent.agent.AgentEvent.FinishReason;
import com.example.java_mind_agent.agent.AgentEvent.Limit;
import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.agent.AgentLimits;
import com.example.java_mind_agent.agent.AgentTexts;
import com.example.java_mind_agent.agent.tool.ToolExecutor;
import com.example.java_mind_agent.agent.tool.ToolOutcome;
import com.example.java_mind_agent.agent.tool.ToolRegistry;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Thinking;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Token;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCallRequest;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCalls;
import com.example.java_mind_agent.models.routing.ModelRouter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * O loop do agente: gera, e se o modelo pedir ferramentas, executa, devolve os resultados e gera de novo, até ele
 * responder sem pedir mais nada ou um limite de {@link AgentLimits} chegar.
 *
 * <ul>
 * <li><b>Um passo</b> é uma chamada ao modelo. Cada passo emite o que acontece nele (status, tokens, pedidos e
 * resultados de ferramentas) assim que acontece.</li>
 * <li><b>Limites</b>: passos, tokens e tempo terminam a execução <em>com a resposta que existe</em>, não com erro. No
 * último passo as ferramentas não são oferecidas e o modelo é mandado fechar a resposta. O limite de chamadas de
 * ferramenta só devolve erro às que passarem dele. Cada ferramenta tem o seu próprio tempo.</li>
 * <li><b>Cancelamento</b>: {@link LoopExecution#cancel()} ou cancelar a assinatura param tudo na hora: a geração
 * em andamento (a conexão com o Ollama é fechada) e a ferramenta que estiver rodando.</li>
 * <li><b>Erros do modelo</b> (Ollama fora do ar, memória, tempo de resposta) saem como erro do fluxo.</li>
 * </ul>
 *
 * O loop não escolhe modelo, não monta prompt e não sabe de conversa: recebe tudo pronto em {@link LoopInput}.
 */
@Component
public class AgentLoop {

	private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

	private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {
	};

	private final ModelRouter router;

	private final ToolRegistry tools;

	private final ToolExecutor executor;

	private final JsonMapper json;

	public AgentLoop(ModelRouter router, ToolRegistry tools, ToolExecutor executor, JsonMapper json) {
		this.router = router;
		this.tools = tools;
		this.executor = executor;
		this.json = json;
	}

	public LoopExecution start(LoopInput input) {
		return new Execution(input);
	}

	/** Por que a execução foi parada de fora. */
	private enum Stop {
		CANCELLED, TIME
	}

	/** O que o modelo disse e pediu em um passo. */
	private static final class Turn {

		final boolean toolsOffered;

		final StringBuilder text = new StringBuilder();

		List<ToolCallRequest> calls = List.of();

		@Nullable Completed completed;

		Turn(boolean toolsOffered) {
			this.toolsOffered = toolsOffered;
		}
	}

	/**
	 * O estado de uma execução. Os sinais do Reactor chegam um de cada vez, então só uma thread escreve por vez; os campos
	 * são {@code volatile} porque o fim por tempo e o cancelamento leem o estado a partir da thread do timer.
	 */
	private final class Execution implements LoopExecution {

		private final LoopInput input;

		private final AgentLimits limits;

		private final AgentTexts texts;

		private final List<Message> messages;

		private final Sinks.Empty<Void> cancelRequest = Sinks.empty();

		private final AtomicReference<@Nullable Stop> stopped = new AtomicReference<>();

		private volatile long startedAt;

		private volatile @Nullable Long firstOutputAt;

		private volatile int steps;

		private volatile int toolCalls;

		private volatile int callSequence;

		private volatile int promptTokens;

		private volatile int completionTokens;

		private volatile int measuredTokens;

		private volatile double measuredSeconds;

		private volatile boolean finished;

		private volatile boolean answeredAtStepLimit;

		Execution(LoopInput input) {
			this.input = input;
			this.limits = input.limits();
			this.texts = AgentTexts.of(input.language());
			this.messages = new ArrayList<>(input.messages());
		}

		@Override
		public void cancel() {
			cancelRequest.tryEmitEmpty();
		}

		@Override
		public Flux<AgentEvent> events() {
			return Flux.defer(() -> {
				startedAt = System.nanoTime();
				Mono<Stop> cancelled = cancelRequest.asMono().thenReturn(Stop.CANCELLED);
				Mono<Stop> timeUp = Mono.delay(limits.maxDuration()).thenReturn(Stop.TIME);
				Mono<Stop> stop = Mono.firstWithValue(cancelled, timeUp).doOnNext(stopped::set);
				return Flux.concat(opening(), step())
					.takeUntilOther(stop)
					.concatWith(Flux.defer(this::stoppedFromOutside));
			});
		}

		// ------------------------------------------------------------------------------ passos

		private Flux<AgentEvent> opening() {
			List<AgentEvent> events = new ArrayList<>(3);
			if (input.notice() != null) {
				events.add(new AgentEvent.Status(Phase.GENERATING, input.notice()));
			}
			if (input.preface() != null) {
				// Sai já, enquanto o modelo carrega; não conta como primeira saída do modelo nas métricas.
				events.add(new AgentEvent.Token(input.preface()));
			}
			if (!input.route().loaded()) {
				events.add(new AgentEvent.Status(Phase.LOADING_MODEL, texts.loadingModel()));
			}
			return Flux.fromIterable(events);
		}

		private Flux<AgentEvent> step() {
			return Flux.defer(() -> {
				steps++;
				boolean lastStep = steps >= limits.maxSteps();
				boolean offerTools = input.toolsEnabled() && !lastStep && toolCalls < limits.maxToolCalls();
				List<AgentEvent> before = new ArrayList<>(2);
				if (!offerTools && messages.getLast() instanceof ToolResponseMessage) {
					// Os resultados das ferramentas já estão no histórico: falta só o modelo fechar a resposta.
					messages.add(new UserMessage(texts.answerNow()));
					if (lastStep) {
						answeredAtStepLimit = true;
						before.add(new AgentEvent.Status(Phase.GENERATING, texts.stepLimit(limits.maxSteps())));
					}
				}
				before.add(new AgentEvent.Status(Phase.GENERATING,
						steps == 1 ? texts.generating() : texts.analyzing(steps)));

				Turn turn = new Turn(offerTools);
				List<ToolCallback> offered = offerTools ? tools.callbacks() : List.of();
				Flux<AgentEvent> generation = router
					.stream(input.route(), List.copyOf(messages), optionsForStep(), offered)
					.concatMap(event -> onModelEvent(turn, event));
				return Flux.concat(Flux.fromIterable(before), generation, Flux.defer(() -> afterModel(turn)));
			});
		}

		/** O orçamento de tokens que sobra vira o teto de uma chamada, para ela não estourá-lo sozinha. */
		private GenerationOptions optionsForStep() {
			int remaining = Math.max(1, limits.maxTokens() - completionTokens);
			GenerationOptions base = input.generation();
			Integer perCall = base.numPredict();
			return base.withNumPredict(perCall != null ? Math.min(perCall, remaining) : remaining);
		}

		private Flux<AgentEvent> onModelEvent(Turn turn, ModelStreamEvent event) {
			return switch (event) {
				case Token token -> {
					markFirstOutput();
					turn.text.append(token.text());
					yield Flux.just(new AgentEvent.Token(token.text()));
				}
				case Thinking thinking -> {
					markFirstOutput();
					yield Flux.just(new AgentEvent.Thinking(thinking.text()));
				}
				case ToolCalls requested -> {
					turn.calls = requested.calls();
					yield Flux.empty();
				}
				case Completed completed -> {
					account(completed);
					turn.completed = completed;
					yield Flux.empty();
				}
			};
		}

		private Flux<AgentEvent> afterModel(Turn turn) {
			List<ToolCallRequest> requests = turn.toolsOffered ? turn.calls : List.of();
			Completed completed = turn.completed;
			if (requests.isEmpty()) {
				boolean cut = completed != null && completed.finishReason() == ModelStreamEvent.FinishReason.LENGTH;
				if (cut) {
					boolean budgetSpent = completionTokens >= limits.maxTokens();
					return finish(FinishReason.LENGTH, budgetSpent ? Limit.TOKENS : null,
							budgetSpent ? texts.tokenLimit(limits.maxTokens()) : null);
				}
				return finish(FinishReason.STOP, answeredAtStepLimit ? Limit.STEPS : null, null);
			}
			return Flux.concat(runTools(turn, requests), Flux.defer(this::afterTools));
		}

		private Flux<AgentEvent> afterTools() {
			if (completionTokens >= limits.maxTokens()) {
				return finish(FinishReason.LENGTH, Limit.TOKENS, texts.tokenLimit(limits.maxTokens()));
			}
			return step();
		}

		// ------------------------------------------------------------------------- ferramentas

		/** Executa uma por vez, na ordem em que o modelo pediu, e guarda tudo no histórico para o próximo passo. */
		private Flux<AgentEvent> runTools(Turn turn, List<ToolCallRequest> requests) {
			List<AssistantMessage.ToolCall> asked = new ArrayList<>(requests.size());
			List<ToolResponseMessage.ToolResponse> answers = new ArrayList<>(requests.size());
			for (ToolCallRequest request : requests) {
				asked.add(new AssistantMessage.ToolCall("call_" + (++callSequence), "function", request.name(),
						request.arguments()));
			}
			messages.add(AssistantMessage.builder().content(turn.text.toString()).toolCalls(asked).build());

			Flux<AgentEvent> runs = Flux.fromIterable(asked).concatMap(call -> runTool(call, answers));
			return Flux.concat(runs, Flux.defer(() -> {
				messages.add(ToolResponseMessage.builder().responses(answers).build());
				return Flux.empty();
			}));
		}

		private Flux<AgentEvent> runTool(AssistantMessage.ToolCall call, List<ToolResponseMessage.ToolResponse> answers) {
			String name = call.name();
			Flux<AgentEvent> announce = Flux.just(new AgentEvent.Status(tools.phaseOf(name), texts.runningTool(name)),
					new AgentEvent.ToolCall(call.id(), name, parse(call.arguments())));
			Mono<AgentEvent> result = Mono.defer(() -> execute(call)).map(outcome -> {
				answers.add(new ToolResponseMessage.ToolResponse(call.id(), name, outcome.output()));
				return new AgentEvent.ToolResult(call.id(), name, outcome.ok(), outcome.summary(), outcome.output(),
						outcome.durationMs());
			});
			return Flux.concat(announce, result);
		}

		private Mono<ToolOutcome> execute(AssistantMessage.ToolCall call) {
			if (toolCalls >= limits.maxToolCalls()) {
				String message = texts.toolBudgetSpent(limits.maxToolCalls());
				return Mono.just(new ToolOutcome(false, message, message, 0));
			}
			toolCalls++;
			return executor.execute(tools.find(call.name()).orElse(null), call.name(), tools.names(), call.arguments(),
					limits.toolTimeout(), limits.maxToolOutputChars(), texts);
		}

		private Map<String, Object> parse(String arguments) {
			if (arguments == null || arguments.isBlank()) {
				return Map.of();
			}
			try {
				return new LinkedHashMap<>(json.readValue(arguments, ARGUMENTS));
			}
			catch (RuntimeException e) {
				log.debug("O modelo mandou argumentos que não são um objeto JSON: {}", arguments);
				return Map.of();
			}
		}

		// ---------------------------------------------------------------------------- o fim

		private Flux<AgentEvent> finish(FinishReason reason, @Nullable Limit limit, @Nullable String notice) {
			finished = true;
			List<AgentEvent> events = new ArrayList<>(3);
			if (notice != null) {
				events.add(new AgentEvent.Status(Phase.GENERATING, notice));
			}
			events.add(usage());
			events.add(new AgentEvent.Finished(reason, limit, steps, toolCalls));
			return Flux.fromIterable(events);
		}

		/** Quando algo de fora (a pessoa ou o relógio) interrompeu: fecha com o que existe. */
		private Flux<AgentEvent> stoppedFromOutside() {
			Stop stop = stopped.get();
			if (stop == null || finished) {
				return Flux.empty();
			}
			return switch (stop) {
				case CANCELLED -> finish(FinishReason.CANCELLED, null, null);
				case TIME -> finish(FinishReason.LENGTH, Limit.TIME, texts.timeLimit(limits.maxDuration()));
			};
		}

		private AgentEvent.Usage usage() {
			Double tokensPerSecond = measuredSeconds > 0 ? measuredTokens / measuredSeconds : null;
			Long firstTokenMs = firstOutputAt != null ? (firstOutputAt - startedAt) / 1_000_000 : null;
			return new AgentEvent.Usage(promptTokens, completionTokens, tokensPerSecond, firstTokenMs,
					(System.nanoTime() - startedAt) / 1_000_000);
		}

		private void account(Completed completed) {
			ModelStreamEvent.Usage usage = completed.usage();
			promptTokens += usage.promptTokens();
			completionTokens += usage.completionTokens();
			Double speed = usage.tokensPerSecond();
			if (speed != null && speed > 0 && usage.completionTokens() > 0) {
				measuredTokens += usage.completionTokens();
				measuredSeconds += usage.completionTokens() / speed;
			}
		}

		private void markFirstOutput() {
			if (firstOutputAt == null) {
				firstOutputAt = System.nanoTime();
			}
		}
	}
}
