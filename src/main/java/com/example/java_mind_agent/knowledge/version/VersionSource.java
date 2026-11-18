package com.example.java_mind_agent.knowledge.version;

/**
 * De onde veio a versão assumida. A ordem do enum é a ordem de prioridade: o que a pessoa disse vale mais que o que
 * ela escolheu na tela, que vale mais que o que o código deixa entrever, que vale mais que as preferências.
 */
public enum VersionSource {

	/** "Java 8", "JDK 17"... na própria pergunta. */
	MENTIONED,
	/** "versão antiga", sem número. */
	LEGACY_HINT,
	/** "última versão", "versão mais recente". */
	LATEST_HINT,
	/** A versão escolhida no seletor, para a conversa. */
	SELECTED,
	/** A versão mínima que o código exige. */
	INFERRED,
	/** A preferência salva ({@code defaultJavaVersion}). */
	SETTINGS,
	/** Nada indicou nada: a LTS mais recente. */
	DEFAULT
}
