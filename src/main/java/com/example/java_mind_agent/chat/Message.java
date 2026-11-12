package com.example.java_mind_agent.chat;

import java.time.Instant;

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

@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Conversation conversation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MessageRole role;

	@Column(nullable = false)
	private String content;

	/** Tier e modelo que produziram a resposta (só em mensagens do assistente). */
	@Column(length = 16)
	private String tier;

	@Column(length = 100)
	private String model;

	/** Versão do Java assumida na resposta. */
	private Integer javaVersion;

	private Integer promptTokens;

	private Integer completionTokens;

	private Double tokensPerSecond;

	/** JSON com as fontes citadas. */
	@JdbcTypeCode(SqlTypes.JSON)
	private String sources;

	/** JSON com a linha do tempo de tool calls. */
	@JdbcTypeCode(SqlTypes.JSON)
	private String toolCalls;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Message(Conversation conversation, MessageRole role, String content) {
		this.conversation = conversation;
		this.role = role;
		this.content = content;
	}
}
