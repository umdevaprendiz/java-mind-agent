package com.example.java_mind_agent.pdf.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.pdf.domain.Answer;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

	Optional<Answer> findFirstByQuestionIdOrderByCreatedAtDesc(UUID questionId);

	List<Answer> findByJobId(UUID jobId);
}
