package com.example.java_mind_agent.pdf.domain;

/** Etapa de processamento do PDF (F25 a F27). */
public enum PdfStatus {
	UPLOADED,
	EXTRACTING,
	EXTRACTED,
	SEGMENTING,
	SEGMENTED,
	FAILED
}
