package com.example.java_mind_agent.knowledge;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * O que um texto diz sobre a versão do Java. Só relata os sinais; decidir qual versão assumir é da
 * {@link JavaVersionService}.
 *
 * @param mentioned       versões citadas, sem repetição e na ordem em que aparecem ("Java 8 e 17" dá {@code [8, 17]})
 * @param unknownMentions citadas, mas fora do registro (ex.: "Java 40"); não são usadas
 * @param legacyHint      pediu uma "versão antiga" sem dizer qual
 * @param latestHint      pediu a "versão mais recente"/"última versão"
 * @param evidence        recursos do código que exigem uma versão, do mais novo para o mais antigo
 */
public record VersionDetection(
		List<Integer> mentioned,
		List<Integer> unknownMentions,
		boolean legacyHint,
		boolean latestHint,
		List<VersionEvidence> evidence) {

	public VersionDetection {
		mentioned = List.copyOf(mentioned);
		unknownMentions = List.copyOf(unknownMentions);
		evidence = List.copyOf(evidence);
	}

	public static VersionDetection empty() {
		return new VersionDetection(List.of(), List.of(), false, false, List.of());
	}

	/** A versão <b>mínima</b> que o código exige: a maior entre os recursos encontrados. */
	public Optional<Integer> inferredMin() {
		return evidence.stream().map(VersionEvidence::minVersion).max(Comparator.naturalOrder());
	}

	/** Recursos que ainda são preview ou incubadora (precisam de flag para rodar). */
	public List<VersionEvidence> previewFeatures() {
		return evidence.stream().filter(VersionEvidence::preview).toList();
	}

	/** Recursos do código que <b>não</b> existem na versão dada: o aviso quando alguém fixa uma versão antiga demais. */
	public List<VersionEvidence> requiringMoreThan(int version) {
		return evidence.stream().filter(e -> e.minVersion() > version).toList();
	}
}
