package com.example.java_mind_agent.models;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Nível de custo do modelo, do mais leve ao mais pesado: o que o usuário escolhe no seletor (Baixo, Médio, Forte,
 * Extra). Na API e no banco o valor é o texto em minúsculas ({@code low}, {@code medium}, {@code strong},
 * {@code extra}); qual modelo do Ollama cada nível usa é configuração ({@code app.models.tiers}).
 */
public enum Tier {

	LOW("low"),
	MEDIUM("medium"),
	STRONG("strong"),
	EXTRA("extra");

	private final String value;

	Tier(String value) {
		this.value = value;
	}

	/** Texto usado na API, no banco ({@code conversation.tier}, {@code app_setting}) e na configuração. */
	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static Tier fromValue(String value) {
		return find(value).orElseThrow(() -> new IllegalArgumentException("Tier desconhecido: " + value));
	}

	public static Optional<Tier> find(String value) {
		return Arrays.stream(values()).filter(t -> t.value.equalsIgnoreCase(value)).findFirst();
	}
}
