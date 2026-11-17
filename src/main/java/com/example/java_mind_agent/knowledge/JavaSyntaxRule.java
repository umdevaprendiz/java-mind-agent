package com.example.java_mind_agent.knowledge;

import java.util.regex.Pattern;

/**
 * Uma regra do detector: "se o código tem isto, ele precisa de Java {@code since} ou mais novo".
 *
 * @param id           identificador estável
 * @param label        o recurso, em português
 * @param since        primeira versão em que funciona sem flags (ou em que apareceu, se ainda for preview)
 * @param previewSince primeira versão em que existiu como preview; {@code 0} = nunca
 * @param preview      ainda é preview ou incubadora na versão mais nova do registro
 * @param view         em qual versão do texto a regex roda
 * @param pattern      o que procurar
 */
record JavaSyntaxRule(
		String id,
		String label,
		int since,
		int previewSince,
		boolean preview,
		View view,
		Pattern pattern) {

	/** Versões do mesmo código, cada uma limpa o suficiente para um tipo de regra. */
	enum View {
		/** Exatamente como veio (blocos de texto ainda inteiros). */
		RAW,
		/** Sem blocos de texto, strings e comentários: palavras dentro deles não contam. */
		CODE,
		/** Igual a {@link #CODE} e sem as setas de {@code case x ->}, para a seta de lambda não se confundir com elas. */
		NO_SWITCH_ARROW
	}
}
