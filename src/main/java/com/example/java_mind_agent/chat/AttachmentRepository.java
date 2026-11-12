package com.example.java_mind_agent.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	List<Attachment> findByConversationId(UUID conversationId);
}
