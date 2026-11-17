package com.example.java_mind_agent.knowledge;

import java.util.List;

/**
 * Uma versão do Java no registro ({@code knowledge/java-versions.json}).
 *
 * @param version     versão major; {@code 1.x} vale {@code x} (o 1 reúne o Java 1.0 e o 1.1)
 * @param label       nome para mostrar, ex.: {@code Java 21}
 * @param releaseYear ano do lançamento
 * @param lts         versão de suporte de longo prazo
 * @param highlights  recursos marcantes, curtos o bastante para o seletor da tela
 * @param removed     o que saiu do JDK nesta versão (insumo da F18, "APIs removidas")
 * @param deprecated  o que foi depreciado nesta versão
 */
public record JavaRelease(
		int version,
		String label,
		int releaseYear,
		boolean lts,
		List<String> highlights,
		List<String> removed,
		List<String> deprecated) {

	public JavaRelease {
		if (version < 1 || version > 99) {
			throw new IllegalArgumentException("Versão do Java fora de 1 a 99: " + version);
		}
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("Java " + version + " sem nome");
		}
		highlights = highlights == null ? List.of() : List.copyOf(highlights);
		if (highlights.isEmpty()) {
			throw new IllegalArgumentException("Java " + version + " sem recursos marcantes");
		}
		removed = removed == null ? List.of() : List.copyOf(removed);
		deprecated = deprecated == null ? List.of() : List.copyOf(deprecated);
	}

	/** O que a API entrega ao front (schema {@code JavaVersionInfo}). */
	public JavaVersionInfo info() {
		return new JavaVersionInfo(version, label, releaseYear, lts, highlights);
	}
}
