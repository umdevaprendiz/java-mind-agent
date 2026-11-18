package com.example.java_mind_agent.pdf.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.java_mind_agent.pdf.domain.JobStatus;
import com.example.java_mind_agent.pdf.domain.PdfJob;

public interface PdfJobRepository extends JpaRepository<PdfJob, UUID> {

	List<PdfJob> findByPdfDocumentIdOrderByCreatedAtDesc(UUID pdfDocumentId);

	/** Usado para retomar lotes interrompidos por um reinício da aplicação. */
	List<PdfJob> findByStatusIn(Collection<JobStatus> statuses);
}
