package com.example.java_mind_agent.pdf;

/** Estado de uma execução de resolução em lote (F28). */
public enum JobStatus {
	PENDING,
	RUNNING,
	PAUSED,
	COMPLETED,
	FAILED,
	CANCELLED
}
