package com.example.java_mind_agent.agent;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * O que o agente conta enquanto trabalha, um evento por acontecimento de cada passo. Espelha os eventos do stream de
 * {@code POST /api/chat} ({@code api/openapi.yaml}); quem monta esse stream (chat, F22) só precisa traduzir, e acrescenta
 * por conta própria o {@code start} e o {@code done} com os ids das mensagens.
 *
 * <p>Uma execução emite, em ordem: {@link Status}, os {@link Token}/{@link Thinking} do passo, e, se o modelo pediu
 * ferramentas, {@link ToolCall} e {@link ToolResult} de cada uma; depois outro passo; no fim, {@link Usage} e
 * {@link Finished}. Falhas (Ollama fora do ar, modelo sem memória, tempo esgotado) saem como erro do
 * {@link reactor.core.publisher.Flux}, não como evento.
 */
public sealed interface AgentEvent {

	/** Em que o agente está, para a tela mostrar. */
	record Status(Phase phase, String message) implements AgentEvent {
	}

	/** Pedaço do raciocínio do modelo; não faz parte da resposta. */
	record Thinking(String text) implements AgentEvent {
	}

	/** Pedaço da resposta, a ser concatenado. */
	record Token(String text) implements AgentEvent {
	}

	/**
	 * O agente decidiu usar uma ferramenta.
	 *
	 * @param callId    liga este evento ao {@link ToolResult} correspondente
	 * @param arguments argumentos que o modelo escreveu; vazio se o JSON dele era inválido (o resultado dirá o erro)
	 */
	record ToolCall(String callId, String tool, Map<String, Object> arguments) implements AgentEvent {
	}

	/**
	 * O resultado de uma ferramenta.
	 *
	 * @param ok      {@code false} se a ferramenta não existe, falhou, estourou o tempo ou passou do limite de chamadas
	 * @param summary linha curta para a linha do tempo
	 * @param output  a saída (ou a mensagem de erro), cortada em {@link AgentLimits#maxToolOutputChars()}; é o mesmo
	 *                texto que o modelo recebe de volta
	 */
	record ToolResult(String callId, String tool, boolean ok, String summary, @Nullable String output,
			long durationMs) implements AgentEvent {
	}

	/**
	 * Métricas da execução inteira, somando todos os passos.
	 *
	 * @param promptTokens       tokens lidos em todos os passos (o histórico é lido de novo a cada passo)
	 * @param completionTokens   tokens gerados em todos os passos
	 * @param tokensPerSecond    velocidade média de geração informada pelo Ollama; {@code null} se não deu para medir
	 * @param timeToFirstTokenMs do início até o primeiro pedaço de resposta, carga do modelo incluída
	 * @param totalDurationMs    duração da execução, ferramentas incluídas
	 */
	record Usage(int promptTokens, int completionTokens, @Nullable Double tokensPerSecond,
			@Nullable Long timeToFirstTokenMs, long totalDurationMs) implements AgentEvent {
	}

	/**
	 * Último evento de uma execução que terminou sem erro.
	 *
	 * @param reason    por que parou
	 * @param limit     o limite que cortou a execução; {@code null} se terminou por conta própria ou foi cancelada
	 * @param steps     chamadas feitas ao modelo
	 * @param toolCalls ferramentas executadas
	 */
	record Finished(FinishReason reason, @Nullable Limit limit, int steps, int toolCalls) implements AgentEvent {
	}

	/** Fases que a tela mostra (valores do contrato: {@code loading_model}, {@code retrieving}, ...). */
	enum Phase {

		LOADING_MODEL,
		RETRIEVING,
		EXECUTING_CODE,
		VERIFYING,
		GENERATING;

		public String value() {
			return name().toLowerCase();
		}
	}

	/** Valores de {@code finishReason} do contrato. */
	enum FinishReason {

		/** O agente terminou a resposta. */
		STOP,
		/** A resposta foi cortada por um {@link Limit} (ou pela janela de contexto do modelo). */
		LENGTH,
		/** A pessoa mandou parar. */
		CANCELLED;

		public String value() {
			return name().toLowerCase();
		}
	}

	/** Qual limite de {@link AgentLimits} fez a execução parar. */
	enum Limit {

		/** Chegou ao último passo e respondeu sem ferramentas; a resposta saiu completa. */
		STEPS,
		/** Gastou o orçamento de tokens; a resposta pode estar cortada. */
		TOKENS,
		/** Estourou a duração total; a resposta está cortada. */
		TIME
	}
}
