package com.example.java_mind_agent.agent;

import java.time.Duration;
import java.util.Collection;

import com.example.java_mind_agent.settings.Language;

/**
 * Os textos que o agente escreve por conta própria (avisos de status, erros de ferramenta devolvidos ao modelo), no
 * idioma da resposta. O que o modelo diz não passa por aqui.
 */
public final class AgentTexts {

	private final boolean pt;

	private AgentTexts(boolean pt) {
		this.pt = pt;
	}

	public static AgentTexts of(Language language) {
		return new AgentTexts(language != Language.EN);
	}

	public String loadingModel() {
		return pt ? "Carregando o modelo na memória" : "Loading the model into memory";
	}

	public String generating() {
		return pt ? "Gerando a resposta" : "Generating the answer";
	}

	public String analyzing(int step) {
		return pt ? "Analisando o resultado das ferramentas (passo " + step + ")"
				: "Analyzing the tool results (step " + step + ")";
	}

	public String runningTool(String tool) {
		return pt ? "Usando a ferramenta " + tool : "Using the " + tool + " tool";
	}

	public String noTools(String model) {
		return pt ? "O modelo " + model + " não confirmou suporte a ferramentas: respondo sem usá-las."
				: "The " + model + " model did not confirm tool support: answering without tools.";
	}

	public String stepLimit(int maxSteps) {
		return pt ? "Limite de " + maxSteps + " passos atingido: respondo com o que já obtive."
				: "Reached the limit of " + maxSteps + " steps: answering with what I have so far.";
	}

	public String tokenLimit(int maxTokens) {
		return pt ? "Limite de " + maxTokens + " tokens gerados atingido: a resposta foi interrompida."
				: "Reached the limit of " + maxTokens + " generated tokens: the answer was cut short.";
	}

	public String timeLimit(Duration maxDuration) {
		return pt ? "Limite de tempo de " + readable(maxDuration) + " atingido: a resposta foi interrompida."
				: "Reached the time limit of " + readable(maxDuration) + ": the answer was cut short.";
	}

	/** Pedido ao modelo, no último passo, para fechar a resposta sem ferramentas. */
	public String answerNow() {
		return pt ? "Você não pode mais chamar ferramentas. Responda agora, com o que já obteve, e avise o que não deu "
				+ "para verificar."
				: "You can no longer call tools. Answer now with what you have, and say what could not be verified.";
	}

	public String unknownTool(String tool, Collection<String> available) {
		return pt ? "A ferramenta '" + tool + "' não existe. Disponíveis: " + String.join(", ", available) + "."
				: "The tool '" + tool + "' does not exist. Available: " + String.join(", ", available) + ".";
	}

	public String toolBudgetSpent(int maxToolCalls) {
		return pt ? "Limite de " + maxToolCalls + " chamadas de ferramenta atingido: esta não foi executada. "
				+ "Responda com o que já tem."
				: "Reached the limit of " + maxToolCalls + " tool calls: this one was not run. Answer with what you have.";
	}

	public String toolTimedOut(Duration timeout) {
		return pt ? "A ferramenta passou de " + readable(timeout) + " e foi interrompida."
				: "The tool ran past " + readable(timeout) + " and was stopped.";
	}

	public String toolFailed(String reason) {
		return pt ? "A ferramenta falhou: " + reason : "The tool failed: " + reason;
	}

	public String truncated(int chars) {
		return pt ? "\n… (saída cortada em " + chars + " caracteres)" : "\n… (output cut at " + chars + " characters)";
	}

	public String emptyOutput() {
		return pt ? "(sem saída)" : "(no output)";
	}

	private static String readable(Duration duration) {
		long seconds = duration.toSeconds();
		if (seconds >= 60 && seconds % 60 == 0) {
			return seconds / 60 + " min";
		}
		return seconds > 0 ? seconds + " s" : duration.toMillis() + " ms";
	}
}
