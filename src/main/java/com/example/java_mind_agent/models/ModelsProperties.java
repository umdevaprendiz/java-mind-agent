package com.example.java_mind_agent.models;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do módulo de modelos (prefixo {@code app.models}).
 *
 * @param defaultTier   tier usado quando nada foi escolhido; a preferência salva em {@code app_setting}
 *                      ({@code default.tier}) tem prioridade sobre este valor
 * @param generation    valores padrão de geração, usados quando a requisição não informa o seu
 * @param capacity      regra para decidir se um modelo cabe nesta máquina
 * @param pull          download de modelos pelo Ollama
 * @param tiers        o modelo de cada um dos quatro tiers; todos são obrigatórios
 * @param embedding     o modelo de embedding do RAG
 * @param startupReport loga na subida o hardware e os tiers inviáveis
 * @param debug         recursos de depuração, desligados fora do perfil dev
 */
@ConfigurationProperties(prefix = "app.models")
public record ModelsProperties(
		@DefaultValue("medium") Tier defaultTier,
		@DefaultValue Generation generation,
		@DefaultValue Capacity capacity,
		@DefaultValue Pull pull,
		Map<Tier, TierProperties> tiers,
		@DefaultValue Embedding embedding,
		@DefaultValue("true") boolean startupReport,
		@DefaultValue Debug debug) {

	public ModelsProperties {
		Map<Tier, TierProperties> configured = tiers == null ? Map.of() : Map.copyOf(tiers);
		List<String> missing = Arrays.stream(Tier.values())
			.filter(t -> !configured.containsKey(t))
			.map(Tier::value)
			.toList();
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(
					"app.models.tiers precisa definir os quatro níveis (low, medium, strong, extra); faltando: "
							+ missing);
		}
		tiers = configured;
	}

	/**
	 * @param temperature       baixa de propósito: explicar Java pede precisão, não criatividade
	 * @param numCtx            janela de contexto; em CPU cada token a mais custa tempo e RAM
	 * @param keepAlive         quanto tempo o Ollama mantém o modelo na memória após o uso (carregar na CPU é lento)
	 * @param firstTokenTimeout espera pelo primeiro pedaço da resposta; inclui carregar o modelo e ler o prompt
	 * @param idleTimeout       tempo máximo sem chegar nada depois que a resposta começou
	 * @param maxDuration       duração total máxima da geração; {@code 0} desliga o limite
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
	 * Um modelo "cabe" quando a RAM que ele pede é menor ou igual à RAM <b>total</b> menos uma reserva para o sistema,
	 * o IDE, o navegador, o Postgres e o Docker. Usa a RAM total e não a livre porque a livre oscila com o que
	 * estiver aberto (aqui costuma sobrar uns 2,5 GB) e marcaria tudo como indisponível; a livre continua exposta
	 * em {@code hardware.freeRamGb}.
	 *
	 * @param overheadFactor quanto multiplicar o tamanho do modelo em disco para estimar a RAM de que ele precisa
	 *                       (pesos + cache de contexto)
	 * @param reservedRamGb  RAM em GiB que não conta como disponível para o modelo
	 */
	public record Capacity(@DefaultValue("1.3") double overheadFactor, @DefaultValue("3") double reservedRamGb) {
	}

	/**
	 * @param idleTimeout      tempo sem chegar nenhum dado do Ollama que faz o download ser dado como travado (o tempo
	 *                         total não é limitado: um modelo de 9 GB leva o que a internet levar). Um download
	 *                         interrompido é retomado de onde parou ao baixar de novo
	 * @param progressInterval intervalo mínimo entre dois eventos de progresso enviados ao front; o Ollama manda
	 *                         dezenas por segundo
	 * @param warmupTimeout    tempo máximo para carregar um modelo na memória (em CPU, um 7B leva ~40 s)
	 */
	public record Pull(
			@DefaultValue("2m") Duration idleTimeout,
			@DefaultValue("250ms") Duration progressInterval,
			@DefaultValue("5m") Duration warmupTimeout) {

		public Pull {
			if (idleTimeout.isNegative() || idleTimeout.isZero() || warmupTimeout.isNegative() || warmupTimeout.isZero()
					|| progressInterval.isNegative()) {
				throw new IllegalArgumentException(
						"app.models.pull: idle-timeout e warmup-timeout precisam ser positivos e progress-interval não pode ser negativo");
			}
		}
	}

	/**
	 * @param label       nome no seletor (Baixo, Médio, Forte, Extra)
	 * @param description frase curta que explica para que serve o nível
	 * @param ollamaModel nome do modelo no Ollama (ex.: {@code qwen2.5:7b})
	 * @param minRamGb    RAM estimada para rodar, em GiB; vale o maior entre este valor e o tamanho real em disco
	 *                    vezes {@link Capacity#overheadFactor()}
	 * @param speed       velocidade esperada nesta máquina
	 * @param baseUrl     Ollama dedicado a este nível (ex.: outro computador com GPU); {@code null} = o padrão.
	 *                    Nesse caso "cabe" é presumido, porque a RAM do outro host não é conhecida
	 */
	public record TierProperties(
			String label,
			@DefaultValue("") String description,
			String ollamaModel,
			double minRamGb,
			@DefaultValue("medium") Speed speed,
			@Nullable String baseUrl) {

		public TierProperties {
			if (label == null || label.isBlank() || ollamaModel == null || ollamaModel.isBlank()) {
				throw new IllegalArgumentException("Todo tier precisa de label e ollama-model");
			}
			if (minRamGb <= 0) {
				throw new IllegalArgumentException("min-ram-gb do tier '" + label + "' precisa ser maior que zero");
			}
			baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.strip();
		}
	}

	/**
	 * @param model    modelo de embedding; trocar exige nova migration ({@code vector(N)}) e reindexar tudo
	 * @param minRamGb RAM estimada para rodar
	 * @param baseUrl  Ollama dedicado; {@code null} = o padrão
	 */
	public record Embedding(
			@DefaultValue("Embeddings") String label,
			@DefaultValue("Transforma a documentação e as perguntas em vetores para a busca (RAG).") String description,
			@DefaultValue("bge-m3") String model,
			@DefaultValue("1.5") double minRamGb,
			@Nullable String baseUrl) {

		public Embedding {
			baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.strip();
		}
	}

	/**
	 * @param endpointEnabled liga {@code POST /internal/models/stream}, que devolve o stream de qualquer modelo local
	 */
	public record Debug(@DefaultValue("false") boolean endpointEnabled) {
	}
}
