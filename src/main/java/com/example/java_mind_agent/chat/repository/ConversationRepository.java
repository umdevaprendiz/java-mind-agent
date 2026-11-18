package com.example.java_mind_agent.chat.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.chat.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	Page<Conversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
