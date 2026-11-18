package com.example.java_mind_agent.agent.loop;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;

import com.example.java_mind_agent.agent.AgentLimits;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;

/**
 * Tudo que o loop precisa para uma execução, já decidido por quem o chama.
 *
 * @param route        o modelo que responde
 * @param messages     o prompt de sistema, o histórico e a pergunta, nessa ordem
 * @param toolsEnabled oferecer as ferramentas ao modelo; {@code false} quando ele não as suporta
 * @param notice       aviso a mostrar no começo (por exemplo, por que não há ferramentas); {@code null} = nenhum
 * @param preface      texto que abre a resposta, emitido como {@link com.example.java_mind_agent.agent.AgentEvent.Token}
 *                     antes de o modelo gerar qualquer coisa (a declaração da versão do Java); {@code null} = nenhum
 * @param limits       limites da execução
 * @param generation   ajustes de geração de cada chamada ao modelo
 * @param language     idioma dos avisos e dos erros de ferramenta
 */
public record LoopInput(
		Route route,
		List<Message> messages,
		boolean toolsEnabled,
		@Nullable String notice,
		@Nullable String preface,
		AgentLimits limits,
		GenerationOptions generation,
		Language language) {
}
