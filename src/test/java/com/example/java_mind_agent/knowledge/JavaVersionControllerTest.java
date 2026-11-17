package com.example.java_mind_agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.support.OpenApiContract;

import tools.jackson.databind.JsonNode;

/**
 * {@code GET /api/knowledge/versions} com o registro de verdade: cada item que sai cumpre o schema
 * {@code JavaVersionInfo} do contrato, que é o que o seletor de versão do front lê.
 */
@WebMvcTest(JavaVersionController.class)
@Import(JavaVersionRegistry.class)
@EnableConfigurationProperties(AppProperties.class) // o WebConfig precisa dele; o slice não varre @ConfigurationProperties
class JavaVersionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listaTodasAsVersoesNaOrdemEComOsCamposDoContrato() throws Exception {
		String corpo = mockMvc.perform(get("/api/knowledge/versions").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.length()").value(27))
			.andExpect(jsonPath("$[0].version").value(1))
			.andExpect(jsonPath("$[7].version").value(8))
			.andExpect(jsonPath("$[7].label").value("Java 8"))
			.andExpect(jsonPath("$[7].releaseYear").value(2014))
			.andExpect(jsonPath("$[7].lts").value(true))
			.andExpect(jsonPath("$[7].highlights[0]").value("Lambdas e referências a métodos"))
			.andExpect(jsonPath("$[8].lts").value(false))
			.andExpect(jsonPath("$[26].version").value(27))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		JsonNode lista = OpenApiContract.le(corpo);
		for (int i = 0; i < lista.size(); i++) {
			assertThat(OpenApiContract.valida("JavaVersionInfo", lista.get(i).toString()))
				.as("item %d: %s", i, lista.get(i))
				.isEmpty();
		}
	}

	@Test
	void naoVazaOQueNaoEDoContrato() throws Exception {
		mockMvc.perform(get("/api/knowledge/versions"))
			.andExpect(jsonPath("$[7].removed").doesNotExist())
			.andExpect(jsonPath("$[7].deprecated").doesNotExist());
	}
}
