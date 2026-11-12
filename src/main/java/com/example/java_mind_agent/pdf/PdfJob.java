package com.example.java_mind_agent.pdf;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uma execução de "resolver todas as questões" de um PDF. */
@Entity
@Table(name = "pdf_job")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PdfJob {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pdf_document_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private PdfDocument pdfDocument;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private JobStatus status = JobStatus.PENDING;

	/** Id do tier usado na resolução (low, medium, strong, extra). */
	@Column(nullable = false, length = 16)
	private String tier;

	private Integer javaVersion;

	@Column(nullable = false)
	private int totalQuestions;

	@Column(nullable = false)
	private int completedQuestions;

	private String errorMessage;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant startedAt;

	private Instant finishedAt;

	public PdfJob(PdfDocument pdfDocument, String tier) {
		this.pdfDocument = pdfDocument;
		this.tier = tier;
	}
}
