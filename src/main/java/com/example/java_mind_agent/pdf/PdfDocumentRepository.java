package com.example.java_mind_agent.pdf;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfDocumentRepository extends JpaRepository<PdfDocument, UUID> {

	Optional<PdfDocument> findBySha256(String sha256);
}
