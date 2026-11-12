package com.example.java_mind_agent.pdf;

public enum QuestionType {
	MULTIPLE_CHOICE,
	TRUE_FALSE,
	ESSAY,
	/** "O que imprime?" / "compila?" — candidata à verificação por execução (F29). */
	CODE_OUTPUT,
	FILL_IN
}
