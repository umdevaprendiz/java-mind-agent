package com.example.java_mind_agent.pdf.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pdf_document_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private PdfDocument pdfDocument;

	/** Ordem de leitura (1..n). */
	@Column(nullable = false)
	private int ordinal;

	/** Numeração como aparece no PDF ("7", "7.a"). */
	@Column(length = 40)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false, length = 24)
	private QuestionType type;

	@Column(nullable = false)
	private String statement;

	private String code;

	/** JSON: lista de {label, text}; null em questões dissertativas. */
	@JdbcTypeCode(SqlTypes.JSON)
	private String alternatives;

	private Integer javaVersion;

	private Integer pageNumber;

	/** Marcada pela extração/segmentação como duvidosa, para revisão do usuário. */
	@Column(nullable = false)
	private boolean needsReview;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public Question(PdfDocument pdfDocument, int ordinal, QuestionType type, String statement) {
		this.pdfDocument = pdfDocument;
		this.ordinal = ordinal;
		this.type = type;
		this.statement = statement;
	}
}
