package com.example.java_mind_agent.models;

import com.fasterxml.jackson.annotation.JsonValue;

/** Velocidade esperada de um modelo nesta máquina, para o seletor avisar antes de o usuário esperar minutos. */
public enum Speed {

	FAST("fast"),
	MEDIUM("medium"),
	SLOW("slow"),
	VERY_SLOW("very_slow");

	private final String value;

	Speed(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
