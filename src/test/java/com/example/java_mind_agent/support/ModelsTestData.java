package com.example.java_mind_agent.support;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import com.example.java_mind_agent.models.ModelsProperties;
import com.example.java_mind_agent.models.ModelsProperties.Capacity;
import com.example.java_mind_agent.models.ModelsProperties.Debug;
import com.example.java_mind_agent.models.ModelsProperties.Embedding;
import com.example.java_mind_agent.models.ModelsProperties.Generation;
import com.example.java_mind_agent.models.ModelsProperties.TierProperties;
import com.example.java_mind_agent.models.Speed;
import com.example.java_mind_agent.models.Tier;

/**
 * {@link ModelsProperties} prontas para os testes, com os mesmos modelos e números do {@code application.yml}. Quem
 * precisa de outro valor parte de {@link #tiers()} e troca só o que importa.
 */
public final class ModelsTestData {

	private ModelsTestData() {
	}

	public static ModelsProperties properties() {
		return properties(Duration.ofSeconds(5), Duration.ofSeconds(5));
	}

	/** Tempos de geração curtos para os testes de timeout não demorarem. */
	public static ModelsProperties properties(Duration firstTokenTimeout, Duration idleTimeout) {
		return new ModelsProperties(Tier.MEDIUM,
				new Generation(0.3, 4096, "10m", firstTokenTimeout, idleTimeout, Duration.ZERO),
				new Capacity(1.3, 3), tiers(), embedding(null), false, new Debug(false));
	}

	public static ModelsProperties properties(Tier defaultTier, Map<Tier, TierProperties> tiers, Embedding embedding) {
		return new ModelsProperties(defaultTier,
				new Generation(0.3, 4096, "10m", Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO),
				new Capacity(1.3, 3), tiers, embedding, false, new Debug(false));
	}

	/** Os quatro tiers como no {@code application.yml}: Baixo 3B, Médio 7B, Forte 14B, Extra 32B. */
	public static Map<Tier, TierProperties> tiers() {
		Map<Tier, TierProperties> tiers = new EnumMap<>(Tier.class);
		tiers.put(Tier.LOW, tier("Baixo", "qwen2.5:3b", 2.5, Speed.FAST, null));
		tiers.put(Tier.MEDIUM, tier("Médio", "qwen2.5:7b", 6, Speed.SLOW, null));
		tiers.put(Tier.STRONG, tier("Forte", "qwen2.5:14b", 12, Speed.VERY_SLOW, null));
		tiers.put(Tier.EXTRA, tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, null));
		return tiers;
	}

	public static TierProperties tier(String label, String model, double minRamGb, Speed speed, String baseUrl) {
		return new TierProperties(label, "Descrição de " + label, model, minRamGb, speed, baseUrl);
	}

	public static Embedding embedding(String baseUrl) {
		return new Embedding("Embeddings", "Vetores para a busca.", "bge-m3", 1.5, baseUrl);
	}
}
