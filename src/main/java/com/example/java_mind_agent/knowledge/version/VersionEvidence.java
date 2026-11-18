package com.example.java_mind_agent.knowledge.version;

/**
 * Um trecho de código que só existe a partir de certa versão do Java.
 *
 * @param rule         id estável da regra (ex.: {@code record})
 * @param label        o recurso, em português, para citar numa resposta
 * @param minVersion   primeira versão em que funciona sem flags; para um recurso ainda em preview, a primeira em que
 *                     apareceu em preview
 * @param previewSince primeira versão em que existiu como preview ({@code 0} = nunca foi preview)
 * @param preview      {@code true} enquanto o recurso ainda é preview ou incubadora: exige {@code --enable-preview}
 *                     ou {@code --add-modules}
 * @param sample       o pedaço do código que casou, curto, só para mostrar
 */
public record VersionEvidence(
		String rule,
		String label,
		int minVersion,
		int previewSince,
		boolean preview,
		String sample) {
}
