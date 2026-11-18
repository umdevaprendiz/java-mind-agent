package com.example.java_mind_agent.agent.tool;

/**
 * Como terminou uma chamada de ferramenta. A mesma {@code output} vai ao modelo, para ele continuar, e à tela.
 *
 * @param ok         a ferramenta rodou e devolveu um resultado
 * @param summary    linha curta para a linha do tempo
 * @param output     saída da ferramenta ou mensagem de erro, já cortada no limite
 * @param durationMs quanto a chamada levou
 */
public record ToolOutcome(boolean ok, String summary, String output, long durationMs) {
}
