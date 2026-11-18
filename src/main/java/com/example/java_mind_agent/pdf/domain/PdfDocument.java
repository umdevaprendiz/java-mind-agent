package com.example.java_mind_agent.pdf.domain;

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

@Entity
@Table(name = "pdf_document")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PdfDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String filename;

	@Column(nullable = false)
	private long sizeBytes;

	/** Hash do conteúdo; único, para reaproveitar um PDF já enviado. */
	@Column(name = "sha256", nullable = false, length = 64, unique = true)
	private String sha256;

	private Integer pageCount;

	@Column(nullable = false)
	private String storagePath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PdfStatus status = PdfStatus.UPLOADED;

	private String errorMessage;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public PdfDocument(String filename, long sizeBytes, String sha256, String storagePath) {
		this.filename = filename;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.storagePath = storagePath;
	}
}
