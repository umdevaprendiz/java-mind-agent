package com.example.java_mind_agent.settings;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Idioma da interface e das respostas: português do Brasil por padrão, com inglês como opção (A5). */
public enum Language {

	PT_BR("pt-BR"),
	EN("en");

	private final String value;

	Language(String value) {
		this.value = value;
	}

	/** Texto usado na API e em {@code app_setting}. */
	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static Language fromValue(String value) {
		return find(value).orElseThrow(() -> new IllegalArgumentException("Idioma desconhecido: " + value));
	}

	public static Optional<Language> find(String value) {
		return Arrays.stream(values()).filter(l -> l.value.equalsIgnoreCase(value)).findFirst();
	}
}
