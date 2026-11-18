package com.example.java_mind_agent.agent;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Até onde uma execução do agente pode ir. O loop respeita todos e, ao chegar em um, termina com o que tem em vez de
 * falhar (ver {@link AgentEvent.Limit}). Em CPU cada passo custa minutos, então os padrões são conservadores.
 *
 * @param maxSteps          máximo de chamadas ao modelo numa execução. O último passo nunca oferece ferramentas: é a
 *                          resposta final. Com {@code 1} o agente não usa ferramentas
 * @param maxToolCalls      máximo de ferramentas executadas na execução inteira; as que passarem disso voltam ao
 *                          modelo como erro, sem executar
 * @param maxTokens         máximo de tokens gerados somando todos os passos (inclui raciocínio)
 * @param maxDuration       duração total máxima da execução, tempo de ferramentas incluído
 * @param toolTimeout       tempo máximo de cada ferramenta; ao estourar, o modelo recebe o erro e pode seguir
 * @param maxToolOutputChars tamanho máximo da saída de uma ferramenta, que vai ao modelo e à tela; o resto é cortado
 */
public record AgentLimits(
		@DefaultValue("6") int maxSteps,
		@DefaultValue("10") int maxToolCalls,
		@DefaultValue("4096") int maxTokens,
		@DefaultValue("15m") Duration maxDuration,
		@DefaultValue("60s") Duration toolTimeout,
		@DefaultValue("8000") int maxToolOutputChars) {

	public AgentLimits {
		if (maxSteps < 1) {
			throw new IllegalArgumentException("maxSteps precisa ser pelo menos 1");
		}
		if (maxToolCalls < 0) {
			throw new IllegalArgumentException("maxToolCalls não pode ser negativo");
		}
		if (maxTokens < 1) {
			throw new IllegalArgumentException("maxTokens precisa ser pelo menos 1");
		}
		if (maxDuration.isZero() || maxDuration.isNegative()) {
			throw new IllegalArgumentException("maxDuration precisa ser positivo");
		}
		if (toolTimeout.isZero() || toolTimeout.isNegative()) {
			throw new IllegalArgumentException("toolTimeout precisa ser positivo");
		}
		if (maxToolOutputChars < 1) {
			throw new IllegalArgumentException("maxToolOutputChars precisa ser pelo menos 1");
		}
	}
}
