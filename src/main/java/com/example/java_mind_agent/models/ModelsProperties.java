package com.example.java_mind_agent.models;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do módulo de modelos (prefixo {@code app.models}). O catálogo de tiers entra aqui na F06.
 *
 * @param generation valores padrão de geração, usados quando a requisição não informa o seu
 * @param debug      recursos de depuração, desligados fora do perfil dev
 */
@ConfigurationProperties(prefix = "app.models")
public record ModelsProperties(@DefaultValue Generation generation, @DefaultValue Debug debug) {

	/**
	 * @param temperature     baixa de propósito: explicar Java pede precisão, não criatividade
	 * @param numCtx          janela de contexto; em CPU cada token a mais custa tempo e RAM
	 * @param keepAlive       quanto tempo o Ollama mantém o modelo na memória após o uso (carregar na CPU é lento)
	 * @param firstTokenTimeout espera pelo primeiro pedaço da resposta; inclui carregar o modelo e ler o prompt
	 * @param idleTimeout     tempo máximo sem chegar nada depois que a resposta começou
	 * @param maxDuration     duração total máxima da geração; {@code 0} desliga o limite
	 */
	public record Generation(
			@DefaultValue("0.3") double temperature,
			@DefaultValue("4096") int numCtx,
			@DefaultValue("10m") String keepAlive,
			@DefaultValue("3m") Duration firstTokenTimeout,
			@DefaultValue("1m") Duration idleTimeout,
			@DefaultValue("0s") Duration maxDuration) {
	}

	/**
	 * @param endpointEnabled liga {@code POST /internal/models/stream}, que devolve o stream de qualquer modelo local
	 */
	public record Debug(@DefaultValue("false") boolean endpointEnabled) {
	}
}
