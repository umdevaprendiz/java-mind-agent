package com.example.java_mind_agent.agent.loop;

import com.example.java_mind_agent.agent.AgentEvent;

import reactor.core.publisher.Flux;

/**
 * Uma execução do loop, pronta para ser assinada uma vez.
 */
public interface LoopExecution {

	/**
	 * Os eventos da execução. Nada acontece até alguém assinar. Cancelar a assinatura (a pessoa fechou a conexão)
	 * fecha a conexão com o Ollama e interrompe a ferramenta em andamento; nesse caso nada mais é emitido.
	 */
	Flux<AgentEvent> events();

	/**
	 * Pede para parar, como o botão "parar" da tela. Diferente de cancelar a assinatura, a execução ainda emite
	 * {@link AgentEvent.Usage} e {@link AgentEvent.Finished} com {@link AgentEvent.FinishReason#CANCELLED}, para quem
	 * assina poder gravar a resposta parcial. Sem efeito antes de assinar ou depois de terminar.
	 */
	void cancel();
}
