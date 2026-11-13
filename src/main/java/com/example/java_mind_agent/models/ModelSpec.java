package com.example.java_mind_agent.models;

import com.example.java_mind_agent.models.ModelInfo.Kind;

/**
 * O que a configuração diz sobre um modelo (um dos quatro tiers ou o de embedding) e em qual Ollama ele mora, antes de
 * perguntar qualquer coisa ao Ollama. O catálogo (F06) e o gerenciador (F07) partem daqui.
 *
 * @param id   {@code low}, {@code medium}, {@code strong}, {@code extra} ou {@code embedding}
 * @param host URL já normalizada do Ollama que guarda este modelo
 */
record ModelSpec(String id, Kind kind, String label, String description, String model, double minRamGb, String host,
		Speed speed) {

	static final String EMBEDDING_ID = "embedding";

	static ModelSpec chat(Tier tier, ModelsProperties.TierProperties properties, OllamaHosts hosts) {
		return new ModelSpec(tier.value(), Kind.CHAT, properties.label(), properties.description(),
				properties.ollamaModel(), properties.minRamGb(), hosts.resolve(properties.baseUrl()),
				properties.speed());
	}

	static ModelSpec embedding(ModelsProperties.Embedding embedding, OllamaHosts hosts) {
		return new ModelSpec(EMBEDDING_ID, Kind.EMBEDDING, embedding.label(), embedding.description(),
				embedding.model(), embedding.minRamGb(), hosts.resolve(embedding.baseUrl()), Speed.FAST);
	}

	/** O nome como o Ollama o informa: {@code bge-m3} e {@code bge-m3:latest} são o mesmo modelo. */
	String normalizedModel() {
		return normalize(model);
	}

	static String normalize(String model) {
		return model.contains(":") ? model : model + ":latest";
	}
}
