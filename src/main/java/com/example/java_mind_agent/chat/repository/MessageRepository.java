package com.example.java_mind_agent.chat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.chat.domain.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findByConversationIdOrderByIdAsc(UUID conversationId);

	long countByConversationId(UUID conversationId);
}
