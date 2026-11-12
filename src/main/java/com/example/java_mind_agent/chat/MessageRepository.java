package com.example.java_mind_agent.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findByConversationIdOrderByIdAsc(UUID conversationId);

	long countByConversationId(UUID conversationId);
}
