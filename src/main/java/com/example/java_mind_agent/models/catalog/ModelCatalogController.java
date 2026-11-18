package com.example.java_mind_agent.models.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/models}: o catálogo que alimenta o seletor Baixo / Médio / Forte / Extra. Sempre responde 200:
 * Ollama fora do ar aparece como {@code HOST_UNREACHABLE} em cada modelo, para a tela mostrar o aviso (com a RAM e a
 * GPU desta máquina) em vez de quebrar.
 */
@RestController
@RequestMapping("/api/models")
class ModelCatalogController {

	private final ModelCatalogService catalog;

	ModelCatalogController(ModelCatalogService catalog) {
		this.catalog = catalog;
	}

	@GetMapping
	ModelCatalog catalog() {
		return catalog.catalog();
	}
}
