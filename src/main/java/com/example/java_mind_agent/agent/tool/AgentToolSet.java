package com.example.java_mind_agent.agent.tool;

import com.example.java_mind_agent.agent.AgentEvent.Phase;

/**
 * Um bean com ferramentas do agente: os métodos anotados com {@link org.springframework.ai.tool.annotation.Tool}
 * viram ferramentas que o modelo pode pedir. Para uma ferramenta nova (executar código, buscar na documentação...),
 * basta um {@code @Component} que implemente esta interface; o {@link ToolRegistry} a encontra sozinho.
 *
 * <p>Os métodos rodam fora das threads do servidor e com tempo limitado ({@code app.agent.limits.tool-timeout}), então
 * podem bloquear. Uma exceção não derruba o agente: o modelo recebe a mensagem como resultado e pode corrigir o
 * pedido. O texto de {@code @Tool(description)} e de {@code @ToolParam(description)} é o que o modelo lê para decidir
 * quando e como usar a ferramenta: escreva-o para ele.
 */
public interface AgentToolSet {

	/**
	 * O que a tela mostra enquanto a ferramenta roda ({@code executing_code} para rodar Java, {@code retrieving} para
	 * buscar na documentação...).
	 *
	 * @param tool nome da ferramenta, como o modelo a chama
	 */
	default Phase phaseOf(String tool) {
		return Phase.GENERATING;
	}
}
