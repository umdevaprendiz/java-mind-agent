package com.example.java_mind_agent.knowledge.corpus;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conjunto de documentação oficial ingerido (ex.: "Javadoc Java 21").
 * Os trechos e embeddings ficam em {@code corpus_chunk}, gerenciada pelo vector store do Spring AI (F12).
 */
@Entity
@Table(name = "corpus_source")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorpusSource {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 200, unique = true)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CorpusSourceKind kind;

	@Column(nullable = false)
	private String baseUrl;

	/** Licença/termos de uso da fonte (A6). */
	private String licenseTerms;

	/** Faixa de versões do Java coberta (major; 1.x vale x). */
	private Integer javaVersionMin;

	private Integer javaVersionMax;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private CorpusSourceStatus status = CorpusSourceStatus.PENDING;

	/** Hash do conteúdo da última ingestão, para ingestão idempotente. */
	@Column(length = 64)
	private String contentHash;

	@Column(nullable = false)
	private int chunkCount;

	private Instant lastIngestedAt;

	private String errorMessage;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public CorpusSource(String name, CorpusSourceKind kind, String baseUrl) {
		this.name = name;
		this.kind = kind;
		this.baseUrl = baseUrl;
	}
}
