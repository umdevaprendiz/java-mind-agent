package com.example.java_mind_agent.knowledge;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/knowledge/versions}: as versões do Java conhecidas, para o seletor de versão da tela. */
@RestController
@RequestMapping("/api/knowledge/versions")
class JavaVersionController {

	private final JavaVersionRegistry registry;

	JavaVersionController(JavaVersionRegistry registry) {
		this.registry = registry;
	}

	@GetMapping
	List<JavaVersionInfo> versions() {
		return registry.infos();
	}
}
