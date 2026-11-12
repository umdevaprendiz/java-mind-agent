package com.example.java_mind_agent.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorpusSourceRepository extends JpaRepository<CorpusSource, UUID> {

	Optional<CorpusSource> findByName(String name);

	List<CorpusSource> findByStatus(CorpusSourceStatus status);
}
