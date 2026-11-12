package com.example.java_mind_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
	void corsPermiteApenasAsOrigensConfiguradas() {
		assertThat(properties.cors().allowedOrigins()).containsExactly("http://localhost:5173");
	}

}
