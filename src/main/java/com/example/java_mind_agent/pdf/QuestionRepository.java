package com.example.java_mind_agent.pdf;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

	List<Question> findByPdfDocumentIdOrderByOrdinalAsc(UUID pdfDocumentId);
}
