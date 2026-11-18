package com.example.java_mind_agent.models.manager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.OllamaAdminClient.PullLine;
import com.example.java_mind_agent.models.ollama.OllamaErrorMapper;

/**
 * Transforma as linhas do {@code /api/pull} em eventos de progresso para a tela. O Ollama manda uma linha por pedaço
 * baixado (dezenas por segundo) e conta cada camada do modelo separadamente; aqui as camadas viram um total só e o
 * ritmo cai para um evento a cada {@code interval}, sem nunca engolir uma mudança de etapa.
 *
 * <p>Não é thread-safe: um download tem um agregador, alimentado em sequência pelo stream.
 */
final class PullProgressAggregator {

	private final String model;

	private final long intervalNanos;

	/** digest da camada → {total, baixado}. */
	private final Map<String, long[]> layers = new HashMap<>();

	private @Nullable String lastStatus;

	private long lastEmitNanos;

	private boolean emittedAny;

	private boolean succeeded;

	PullProgressAggregator(String model, Duration interval) {
		this.model = model;
		this.intervalNanos = interval.toNanos();
	}

	/**
	 * Processa uma linha do Ollama.
	 *
	 * @param nowNanos relógio monotônico ({@link System#nanoTime()}); parâmetro para os testes controlarem o tempo
	 * @return o evento a enviar ou vazio, se a linha só atualizou a contagem ou veio cedo demais depois do último
	 * @throws ModelException se o Ollama avisou um erro no meio do download
	 */
	Optional<PullEvent.Progress> accept(PullLine line, long nowNanos) {
		if (line.error() != null && !line.error().isBlank()) {
			throw OllamaErrorMapper.fromPullMessage(model, line.error(), 200);
		}
		String raw = line.status() == null ? "" : line.status().strip();
		if (raw.equals("success")) {
			succeeded = true;
			return Optional.empty();
		}

		boolean layer = line.digest() != null && line.total() != null && line.total() > 0;
		if (layer) {
			long[] counts = layers.computeIfAbsent(line.digest(), digest -> new long[2]);
			counts[0] = Math.max(counts[0], line.total());
			long done = line.completed() == null ? 0 : line.completed();
			counts[1] = Math.max(counts[1], Math.min(done, counts[0]));
		}

		String status = layer ? "downloading" : raw;
		if (status.isEmpty()) {
			return Optional.empty();
		}
		boolean changed = !status.equals(lastStatus);
		boolean due = !emittedAny || nowNanos - lastEmitNanos >= intervalNanos;
		if (!changed && !due) {
			return Optional.empty();
		}
		lastStatus = status;
		lastEmitNanos = nowNanos;
		emittedAny = true;
		return Optional.of(snapshot(status));
	}

	/** O Ollama confirmou com {@code "success"}. Sem isso, um stream que acaba não prova que o modelo foi baixado. */
	PullEvent finish() {
		if (!succeeded) {
			throw new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
					"O download de '" + model + "' terminou sem a confirmação do Ollama (a conexão pode ter caído). "
							+ "Baixe de novo: continua de onde parou.",
					true);
		}
		return new PullEvent.Done();
	}

	private PullEvent.Progress snapshot(String status) {
		long total = 0;
		long completed = 0;
		for (long[] counts : layers.values()) {
			total += counts[0];
			completed += counts[1];
		}
		if (total <= 0) {
			return new PullEvent.Progress(status, null, null, null);
		}
		double percent = Math.min(100.0, Math.round(completed * 1000.0 / total) / 10.0);
		return new PullEvent.Progress(status, completed, total, percent);
	}
}
