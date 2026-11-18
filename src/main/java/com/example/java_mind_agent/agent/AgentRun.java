package com.example.java_mind_agent.agent;

import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.agent.loop.LoopExecution;

import reactor.core.publisher.Flux;

/**
 * Uma execução do agente já preparada: o modelo foi escolhido e conferido, a versão do Java foi decidida e o prompt
 * foi montado. É aqui que quem expõe o chat pega o que o evento {@code start} precisa ({@link #route()},
 * {@link #javaVersion()}) antes de abrir o stream, e onde as falhas "antes do stream" (modelo não instalado, Ollama
 * fora do ar) já aconteceram, como exceção de {@link AgentService#prepare}.
 *
 * <p>Nada roda até alguém assinar {@link #events()}; só se assina uma vez.
 */
public final class AgentRun {

	private final Route route;

	private final AssumedVersion javaVersion;

	private final boolean toolsEnabled;

	private final LoopExecution execution;

	AgentRun(Route route, AssumedVersion javaVersion, boolean toolsEnabled, LoopExecution execution) {
		this.route = route;
		this.javaVersion = javaVersion;
		this.toolsEnabled = toolsEnabled;
		this.execution = execution;
	}

	/** O nível, o modelo e o servidor que vão responder. */
	public Route route() {
		return route;
	}

	/** A versão do Java que a resposta vai assumir e declarar. */
	public AssumedVersion javaVersion() {
		return javaVersion;
	}

	/** O modelo recebe ferramentas nesta execução (ele as suporta e há alguma registrada). */
	public boolean toolsEnabled() {
		return toolsEnabled;
	}

	/** Os eventos da execução, em ordem; ver {@link AgentEvent}. */
	public Flux<AgentEvent> events() {
		return execution.events();
	}

	/**
	 * Pede para parar. A execução termina com {@link AgentEvent.FinishReason#CANCELLED} (precedido do uso até ali),
	 * para a resposta parcial poder ser gravada. Pedir antes de assinar faz a execução já nascer cancelada. Para
	 * simplesmente desistir, cancele a assinatura de {@link #events()}.
	 */
	public void cancel() {
		execution.cancel();
	}
}
