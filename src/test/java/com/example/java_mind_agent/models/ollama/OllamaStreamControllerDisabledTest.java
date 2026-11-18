package com.example.java_mind_agent.models.ollama;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.java_mind_agent.common.config.AppProperties;

/**
 * Com {@code app.models.debug.endpoint-enabled=false} (o padrão fora do perfil dev), o endpoint de depuração
 * simplesmente não existe. A propriedade é fixada aqui porque o perfil dev, que é o padrão local, a liga.
 */
@WebMvcTest(OllamaStreamController.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.models.debug.endpoint-enabled=false")
class OllamaStreamControllerDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OllamaChatService chat;

	@Test
	void endpointInternoNaoExistePorPadrao() throws Exception {
		mockMvc.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
			.content("{\"model\":\"m\",\"prompt\":\"oi\"}")).andExpect(status().isNotFound());
	}
}
