package com.example.java_mind_agent.agent.tool;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.agent.AgentTexts;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executa uma ferramenta pedida pelo modelo. Nunca falha: ferramenta inexistente, argumentos inválidos, exceção ou
 * tempo esgotado viram um {@link ToolOutcome} com {@code ok = false}, que o modelo lê e pode corrigir. Cancelar a
 * assinatura interrompe a thread que está executando.
 */
@Component
public class ToolExecutor {

	private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

	private static final int SUMMARY_CHARS = 120;

	private final JsonMapper json;

	public ToolExecutor(JsonMapper json) {
		this.json = json;
	}

	/**
	 * @param tool           a ferramenta, ou {@code null} se o modelo pediu uma que não existe
	 * @param name           nome pedido, para a mensagem de erro
	 * @param available      nomes que existem, para a mensagem de erro
	 * @param arguments      JSON dos argumentos, como o modelo escreveu
	 * @param timeout        tempo máximo da execução
	 * @param maxOutputChars tamanho máximo da saída
	 */
	public Mono<ToolOutcome> execute(@Nullable ToolCallback tool, String name, Collection<String> available,
			String arguments, Duration timeout, int maxOutputChars, AgentTexts texts) {
		if (tool == null) {
			return Mono.just(failure(texts.unknownTool(name, available), 0, maxOutputChars, texts));
		}
		return Mono.defer(() -> {
			long startedAt = System.nanoTime();
			return Mono.fromCallable(() -> tool.call(arguments, new ToolContext(Map.of())))
				.subscribeOn(Schedulers.boundedElastic())
				.timeout(timeout)
				.map(raw -> success(raw, millisSince(startedAt), maxOutputChars, texts))
				.onErrorResume(error -> Mono.just(failure(describe(error, timeout, texts), millisSince(startedAt),
						maxOutputChars, texts)));
		});
	}

	private ToolOutcome success(String raw, long durationMs, int maxOutputChars, AgentTexts texts) {
		String text = unquote(raw);
		String output = text.isBlank() ? texts.emptyOutput() : cut(text, maxOutputChars, texts);
		return new ToolOutcome(true, summaryOf(output), output, durationMs);
	}

	private static ToolOutcome failure(String message, long durationMs, int maxOutputChars, AgentTexts texts) {
		String output = cut(message, maxOutputChars, texts);
		return new ToolOutcome(false, summaryOf(output), output, durationMs);
	}

	private static String describe(Throwable error, Duration timeout, AgentTexts texts) {
		if (error instanceof TimeoutException) {
			return texts.toolTimedOut(timeout);
		}
		log.debug("Ferramenta falhou", error);
		Throwable cause = error;
		while (cause.getCause() != null && cause.getMessage() == null) {
			cause = cause.getCause();
		}
		String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
		return texts.toolFailed(message);
	}

	/** O conversor padrão do Spring AI devolve o retorno em JSON; uma {@code String} chega entre aspas. */
	private String unquote(String raw) {
		if (raw != null && raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
			try {
				return json.readValue(raw, String.class);
			}
			catch (RuntimeException e) {
				return raw;
			}
		}
		return raw == null ? "" : raw;
	}

	private static String cut(String text, int maxChars, AgentTexts texts) {
		return text.length() <= maxChars ? text : text.substring(0, maxChars) + texts.truncated(maxChars);
	}

	/** A primeira linha que tem texto, encurtada. */
	private static String summaryOf(String output) {
		String line = output.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
		return line.length() <= SUMMARY_CHARS ? line : line.substring(0, SUMMARY_CHARS - 1) + "…";
	}

	private static long millisSince(long nanoTime) {
		return (System.nanoTime() - nanoTime) / 1_000_000;
	}
}
