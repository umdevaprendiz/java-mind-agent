package com.example.java_mind_agent.pdf;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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

/** Resposta do agente a uma questão. Pode haver várias por questão (reexecuções); vale a mais recente. */
@Entity
@Table(name = "answer")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Question question;

	/** Execução em lote que gerou a resposta; null se foi gerada fora de um lote. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "job_id")
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private PdfJob job;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AnswerStatus status;

	@Column(length = 10)
	private String chosenAlternative;

	private String explanation;

	/** JSON com o passo a passo. */
	@JdbcTypeCode(SqlTypes.JSON)
	private String steps;

	/** Versão do Java assumida. */
	private Integer javaVersion;

	/** Confiança de 0 a 1. */
	private Double confidence;

	/** JSON com as fontes citadas. */
	@JdbcTypeCode(SqlTypes.JSON)
	private String sources;

	@Column(nullable = false)
	private boolean verifiedByExecution;

	private String executionOutput;

	@Column(length = 16)
	private String tier;

	@Column(length = 100)
	private String model;

	private String errorMessage;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Answer(Question question, AnswerStatus status) {
		this.question = question;
		this.status = status;
	}
}
