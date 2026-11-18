package com.example.java_mind_agent.pdf.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.pdf.domain.Question;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

	List<Question> findByPdfDocumentIdOrderByOrdinalAsc(UUID pdfDocumentId);
}
