package com.example.java_mind_agent.pdf.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.pdf.domain.PdfDocument;

public interface PdfDocumentRepository extends JpaRepository<PdfDocument, UUID> {

	Optional<PdfDocument> findBySha256(String sha256);
}
