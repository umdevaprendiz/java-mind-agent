package com.example.java_mind_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.java_mind_agent.common.config.AppProperties;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JavaMindAgentApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppProperties properties;

	@Test
	void contextLoads() {
	}

	@Test
	void healthEstaUpSemExigirLogin() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void servePeloBackendOContratoDaApi() throws Exception {
		mockMvc.perform(get("/api-contract/openapi.yaml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("openapi: 3.1.0")));
	}

	@Test
	void swaggerUiListaOContratoComoPrimeiraDefinicao() throws Exception {
		mockMvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.urls[0].url").value("/api-contract/openapi.yaml"))
				.andExpect(jsonPath("$.urls[1].url").value("/v3/api-docs"));
	}

	@Test
	void corsPermiteApenasAsOrigensConfiguradas() {
		assertThat(properties.cors().allowedOrigins()).containsExactly("http://localhost:5173");
	}

}
