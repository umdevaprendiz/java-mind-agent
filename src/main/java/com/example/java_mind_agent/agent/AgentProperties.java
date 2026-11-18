package com.example.java_mind_agent.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do agente (prefixo {@code app.agent}).
 *
 * @param limits limites padrão de cada execução; uma requisição pode pedir outros em {@link AgentRequest}
 * @param debug  recursos de depuração, desligados fora do perfil dev
 */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(@DefaultValue AgentLimits limits, @DefaultValue Debug debug) {

	/**
	 * @param endpointEnabled liga {@code POST /internal/agent/run}, para fazer perguntas ao agente sem o chat, e duas
	 *                        ferramentas de demonstração ({@code java_release_info} e {@code current_date_time}) para
	 *                        ver o loop de ferramentas funcionando
	 */
	public record Debug(@DefaultValue("false") boolean endpointEnabled) {
	}
}
