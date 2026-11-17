package com.example.java_mind_agent.knowledge;

import java.util.List;
import java.util.stream.Collectors;

import com.example.java_mind_agent.settings.Language;

/**
 * A versão do Java em que uma resposta vai se basear e por quê. A resposta <b>sempre declara</b> a versão assumida:
 * {@link #statement(Language)} devolve a frase pronta.
 *
 * @param version         versão major assumida
 * @param label           nome para mostrar, ex.: {@code Java 17}
 * @param source          de onde veio
 * @param orNewer         {@code true} quando a versão é só um piso (inferida pelo código): "16 ou mais nova"
 * @param mentioned       todas as versões citadas no texto (a assumida é a maior); a F18 usa para comparar versões
 * @param evidence        recursos do código que sustentam a versão inferida
 * @param conflicts       recursos do código que <b>não existem</b> na versão assumida (quando ela veio de uma citação
 *                        ou da escolha da pessoa)
 * @param previewFeatures recursos em preview que o código usa
 */
public record AssumedVersion(
		int version,
		String label,
		VersionSource source,
		boolean orNewer,
		List<Integer> mentioned,
		List<VersionEvidence> evidence,
		List<VersionEvidence> conflicts,
		List<VersionEvidence> previewFeatures) {

	private static final int MAX_LISTED = 3;

	public AssumedVersion {
		mentioned = List.copyOf(mentioned);
		evidence = List.copyOf(evidence);
		conflicts = List.copyOf(conflicts);
		previewFeatures = List.copyOf(previewFeatures);
	}

	/** A frase que a resposta (ou o prompt do agente) usa para declarar a versão. */
	public String statement(Language language) {
		boolean pt = language == Language.PT_BR;
		StringBuilder text = new StringBuilder(pt ? "Versão do Java assumida: " : "Java version assumed: ");
		text.append(label);
		if (orNewer) {
			text.append(pt ? " ou mais nova" : " or newer");
		}
		text.append(" (").append(reason(pt)).append(").");
		if (!conflicts.isEmpty()) {
			text.append(pt ? " Atenção: o código usa " : " Warning: the code uses ")
				.append(listed(conflicts))
				.append(pt ? ", que só existe em versões mais novas." : ", which only exists in newer versions.");
		}
		if (!previewFeatures.isEmpty()) {
			text.append(pt ? " O código usa " : " The code uses ")
				.append(listed(previewFeatures))
				.append(pt ? ", ainda em preview: precisa de --enable-preview." : ", still a preview feature: it needs --enable-preview.");
		}
		return text.toString();
	}

	private String reason(boolean pt) {
		return switch (source) {
			case MENTIONED -> mentioned.size() > 1
					? (pt ? "você citou " + versions(true) + "; uso a mais nova"
							: "you mentioned " + versions(false) + "; using the newest")
					: (pt ? "citada na pergunta" : "mentioned in the question");
			case LEGACY_HINT -> pt ? "você pediu uma versão antiga, sem dizer qual" : "you asked for an old version without saying which";
			case LATEST_HINT -> pt ? "você pediu a versão mais recente" : "you asked for the latest version";
			case SELECTED -> pt ? "a versão escolhida para esta conversa" : "the version chosen for this conversation";
			case INFERRED -> (pt ? "o código usa " : "the code uses ") + listed(evidence);
			case SETTINGS -> pt ? "sua preferência salva" : "your saved preference";
			case DEFAULT -> pt ? "nenhuma versão indicada; uso a LTS mais recente"
					: "no version indicated; using the latest LTS";
		};
	}

	private String versions(boolean pt) {
		return mentioned.stream().map(v -> "Java " + v).collect(Collectors.joining(", "));
	}

	/** Os recursos mais exigentes, no máximo três, para a frase não virar uma lista. */
	private static String listed(List<VersionEvidence> evidence) {
		return evidence.stream().limit(MAX_LISTED).map(VersionEvidence::label).collect(Collectors.joining(", "));
	}
}
