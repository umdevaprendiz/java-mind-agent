package com.example.java_mind_agent.chat.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Arquivo anexado a uma conversa (código .java, zip, stack trace). PDFs de questões usam PdfDocument. */
@Entity
@Table(name = "attachment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Conversation conversation;

	/** Mensagem a que o anexo pertence; null enquanto o usuário ainda não enviou. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "message_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Message message;

	@Column(nullable = false)
	private String filename;

	@Column(length = 100)
	private String contentType;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(name = "sha256", nullable = false, length = 64)
	private String sha256;

	@Column(nullable = false)
	private String storagePath;

	private String extractedText;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Attachment(Conversation conversation, String filename, long sizeBytes, String sha256, String storagePath) {
		this.conversation = conversation;
		this.filename = filename;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.storagePath = storagePath;
	}
}
