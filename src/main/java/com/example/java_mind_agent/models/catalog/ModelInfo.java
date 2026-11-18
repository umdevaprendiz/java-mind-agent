package com.example.java_mind_agent.models.catalog;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonValue;

import com.example.java_mind_agent.models.Speed;

/**
 * Um modelo do catálogo (schema {@code ModelInfo} do contrato): um dos quatro tiers de chat ou o modelo de embedding.
 *
 * @param id                      {@code low}, {@code medium}, {@code strong}, {@code extra} ou {@code embedding}
 * @param ollamaModel             nome do modelo no Ollama
 * @param remoteHost              URL do Ollama dedicado a este modelo; {@code null} = o Ollama padrão
 * @param sizeGb                  tamanho em disco, conhecido depois de instalado
 * @param minRamGb                RAM mínima configurada para rodar
 * @param installed               o Ollama (do host do modelo) tem o modelo
 * @param loaded                  está carregado na memória agora
 * @param fits                    cabe nesta máquina (em host remoto, presume-se que sim)
 * @param supportsTools           suporta chamada de ferramentas; {@code null} enquanto não instalado
 * @param available               pode ser usado agora: {@code unavailableReason == null}
 * @param unavailableReason       por que não pode, em ordem de prioridade: host fora do ar, não cabe, não instalado
 * @param recommended             o tier que o app sugere para esta máquina
 * @param measuredTokensPerSecond velocidade medida (preenchida pelas F36/F45)
 * @param diskUsageGb             espaço ocupado em disco
 */
public record ModelInfo(
		String id,
		Kind kind,
		String label,
		String description,
		String ollamaModel,
		@Nullable String remoteHost,
		@Nullable Double sizeGb,
		double minRamGb,
		boolean installed,
		boolean loaded,
		boolean fits,
		@Nullable Boolean supportsTools,
		boolean available,
		@Nullable UnavailableReason unavailableReason,
		boolean recommended,
		Speed speed,
		@Nullable Double measuredTokensPerSecond,
		@Nullable Double diskUsageGb) {

	public ModelInfo withRecommended(boolean value) {
		return new ModelInfo(id, kind, label, description, ollamaModel, remoteHost, sizeGb, minRamGb, installed, loaded,
				fits, supportsTools, available, unavailableReason, value, speed, measuredTokensPerSecond,
				diskUsageGb);
	}

	public enum Kind {

		CHAT("chat"),
		EMBEDDING("embedding");

		private final String value;

		Kind(String value) {
			this.value = value;
		}

		@JsonValue
		public String value() {
			return value;
		}
	}

	/** Mesmos nomes do schema {@code ModelUnavailableReason} do contrato. */
	public enum UnavailableReason {
		NOT_INSTALLED,
		DOES_NOT_FIT,
		HOST_UNREACHABLE
	}
}
