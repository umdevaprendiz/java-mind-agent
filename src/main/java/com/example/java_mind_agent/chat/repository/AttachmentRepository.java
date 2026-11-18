package com.example.java_mind_agent.chat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.chat.domain.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	List<Attachment> findByConversationId(UUID conversationId);
}
