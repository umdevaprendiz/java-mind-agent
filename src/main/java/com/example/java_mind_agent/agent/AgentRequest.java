package com.example.java_mind_agent.agent;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.settings.Language;

/**
 * Um pedido ao agente. Campo {@code null} = usar o padrão (as preferências salvas, depois a configuração).
 *
 * @param message    a pergunta da pessoa (o exercício, o texto extraído do PDF...)
 * @param history    mensagens anteriores da conversa, do mais antigo ao mais novo, sem o prompt de sistema; quem
 *                   monta (memória, F20) decide o que cabe na janela de contexto
 * @param tier       nível do modelo; {@code null} = o nível padrão
 * @param javaVersion versão do Java fixada pela pessoa para esta conversa
 * @param language   idioma da resposta
 * @param teaching   modo de ensino (F19); o agente repassa aos contribuidores do prompt, e sem nenhum que o conheça
 *                   ele é ignorado
 * @param limits     limites desta execução; {@code null} = {@code app.agent.limits}
 * @param generation ajustes de geração (temperatura, raciocínio...); {@code null} = padrões de {@code app.models}
 */
public record AgentRequest(
		String message,
		List<Message> history,
		@Nullable Tier tier,
		@Nullable Integer javaVersion,
		@Nullable Language language,
		@Nullable String teaching,
		@Nullable AgentLimits limits,
		@Nullable GenerationOptions generation) {

	public AgentRequest {
		Assert.hasText(message, "message é obrigatório");
		history = history == null ? List.of() : List.copyOf(history);
	}

	/** Só a pergunta; todo o resto no padrão. */
	public static AgentRequest of(String message) {
		return new AgentRequest(message, List.of(), null, null, null, null, null, null);
	}
}
