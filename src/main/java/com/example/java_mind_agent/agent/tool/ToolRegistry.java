package com.example.java_mind_agent.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.agent.AgentEvent.Phase;

/**
 * As ferramentas que o agente pode oferecer ao modelo: tudo que os {@link AgentToolSet} do contexto declaram com
 * {@code @Tool}. Montado uma vez, na subida; nome repetido entre ferramentas impede a aplicação de subir, porque o
 * modelo não teria como saber qual é qual.
 */
@Component
public class ToolRegistry {

	private final Map<String, Registered> byName;

	private final List<String> names;

	private final List<ToolCallback> callbacks;

	public ToolRegistry(List<AgentToolSet> sets) {
		Map<String, Registered> found = new TreeMap<>();
		for (AgentToolSet set : sets) {
			for (ToolCallback callback : ToolCallbacks.from(set)) {
				String name = callback.getToolDefinition().name();
				Registered previous = found.put(name, new Registered(callback, set));
				if (previous != null) {
					throw new IllegalStateException("Ferramenta '" + name + "' declarada duas vezes: em "
							+ previous.set().getClass().getName() + " e em " + set.getClass().getName());
				}
			}
		}
		this.byName = Map.copyOf(found);
		this.names = List.copyOf(found.keySet());
		this.callbacks = found.values().stream().map(Registered::callback).toList();
	}

	/**
	 * As definições a oferecer ao modelo, em ordem alfabética. A ordem fixa mantém o pedido igual de uma execução
	 * para outra, e o Ollama reaproveita o que já leu do começo do prompt.
	 */
	public List<ToolCallback> callbacks() {
		return callbacks;
	}

	/** Os nomes das ferramentas, em ordem alfabética. */
	public List<String> names() {
		return names;
	}

	public boolean isEmpty() {
		return byName.isEmpty();
	}

	public Optional<ToolCallback> find(String name) {
		return Optional.ofNullable(byName.get(name)).map(Registered::callback);
	}

	/** O que mostrar enquanto a ferramenta roda; {@link Phase#GENERATING} se ela não existe. */
	public Phase phaseOf(String name) {
		Registered registered = byName.get(name);
		return registered == null ? Phase.GENERATING : registered.set().phaseOf(name);
	}

	private record Registered(ToolCallback callback, AgentToolSet set) {
	}
}
