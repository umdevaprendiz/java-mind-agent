package com.example.java_mind_agent.chat.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 200)
	private String title = "Nova conversa";

	/** Id do tier de modelo (low, medium, strong, extra); null = padrão. */
	@Column(length = 16)
	private String tier;

	/** Versão major do Java (1.x vale x); null = automática. */
	private Integer javaVersion;

	@Column(nullable = false, length = 8)
	private String language = "pt-BR";

	/** JSON com preferências extras (nível de ensino, modo prova...). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String preferences = "{}";

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public Conversation(String title) {
		this.title = title;
	}
}
