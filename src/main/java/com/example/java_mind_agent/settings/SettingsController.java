package com.example.java_mind_agent.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** {@code GET/PUT /api/settings}: as preferências que valem quando a requisição não escolhe outra coisa. */
@RestController
@RequestMapping("/api/settings")
class SettingsController {

	private final SettingsService service;

	SettingsController(SettingsService service) {
		this.service = service;
	}

	@GetMapping
	Settings get() {
		return service.get();
	}

	@PutMapping
	Settings update(@Valid @RequestBody Settings settings) {
		return service.update(settings);
	}
}
