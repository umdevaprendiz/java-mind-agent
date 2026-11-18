/**
 * Núcleo do agente autônomo: loop de raciocínio, tools, modos de ensino, memória e guardrails
 * (F15 a F21). Lane: Agente.
 *
 * <p>A entrada é {@link com.example.java_mind_agent.agent.AgentService#prepare}, que devolve uma
 * {@link com.example.java_mind_agent.agent.AgentRun} com os eventos ({@link com.example.java_mind_agent.agent.AgentEvent})
 * da execução. Cada assunto tem o seu subpacote: {@code loop} (gerar, executar ferramentas, repetir, com limites e
 * cancelamento), {@code tool} (as ferramentas que o modelo pode pedir) e {@code prompt} (o prompt de sistema e quem o
 * estende). Na raiz ficam só os tipos que todos usam: pedido, eventos, limites e propriedades.
 */
package com.example.java_mind_agent.agent;
