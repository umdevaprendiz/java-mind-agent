package com.example.java_mind_agent.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.support.OpenApiContract;

/** {@code GET/PUT /api/settings} contra o schema {@code Settings} do contrato; a gravação em si está em {@link SettingsServiceDbTest}. */
@WebMvcTest(SettingsController.class)
@EnableConfigurationProperties(AppProperties.class) // o WebConfig precisa dele; o slice não varre @ConfigurationProperties
class SettingsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SettingsService service;

	@Test
	void devolveAsPreferenciasNoFormatoDoContratoComJavaAutomaticoComoNull() throws Exception {
		when(service.get()).thenReturn(new Settings(Tier.MEDIUM, null, Language.PT_BR));

		String corpo = mockMvc.perform(get("/api/settings"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.defaultTier").value("medium"))
			.andExpect(jsonPath("$.defaultJavaVersion").value((Object) null))
			.andExpect(jsonPath("$.language").value("pt-BR"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(corpo).contains("\"defaultJavaVersion\":null");
		assertThat(OpenApiContract.valida("Settings", corpo)).isEmpty();
	}

	@Test
	void gravaAsTresPreferenciasEDevolveOQueFicou() throws Exception {
		Settings gravadas = new Settings(Tier.STRONG, 17, Language.EN);
		when(service.update(any())).thenReturn(gravadas);

		String corpo = mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
			.content("{\"defaultTier\":\"strong\",\"defaultJavaVersion\":17,\"language\":\"en\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultTier").value("strong"))
			.andExpect(jsonPath("$.defaultJavaVersion").value(17))
			.andExpect(jsonPath("$.language").value("en"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		verify(service).update(gravadas);
		assertThat(OpenApiContract.valida("Settings", corpo)).isEmpty();
	}

	@Test
	void javaNullVoltaAoAutomatico() throws Exception {
		when(service.update(any())).thenAnswer(chamada -> chamada.getArgument(0));

		mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
			.content("{\"defaultTier\":\"low\",\"defaultJavaVersion\":null,\"language\":\"pt-BR\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultJavaVersion").value((Object) null));

		verify(service).update(new Settings(Tier.LOW, null, Language.PT_BR));
	}

	@Test
	void aceitaQualquerUmDosQuatroTiersMesmoOQueNaoCabeNestaMaquina() throws Exception {
		when(service.update(any())).thenAnswer(chamada -> chamada.getArgument(0));

		mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
			.content("{\"defaultTier\":\"extra\",\"defaultJavaVersion\":null,\"language\":\"pt-BR\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultTier").value("extra"));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {
			"{\"defaultTier\":\"huge\",\"defaultJavaVersion\":null,\"language\":\"pt-BR\"}",
			"{\"defaultTier\":\"low\",\"defaultJavaVersion\":null,\"language\":\"fr\"}",
			"{\"defaultTier\":\"low\",\"defaultJavaVersion\":0,\"language\":\"pt-BR\"}",
			"{\"defaultTier\":\"low\",\"defaultJavaVersion\":100,\"language\":\"pt-BR\"}",
			"{\"defaultTier\":\"low\",\"defaultJavaVersion\":\"vinte\",\"language\":\"pt-BR\"}",
			"{\"defaultJavaVersion\":null,\"language\":\"pt-BR\"}",
			"{\"defaultTier\":\"low\",\"defaultJavaVersion\":null}",
			"{\"defaultTier\":null,\"defaultJavaVersion\":null,\"language\":null}",
			"não é json" })
	void preferenciaInvalidaEhRecusadaComProblemJsonESemGravarNada(String corpoInvalido) throws Exception {
		String resposta = mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
			.content(corpoInvalido))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		verify(service, never()).update(any());
		assertThat(OpenApiContract.valida("ProblemDetail", resposta)).isEmpty();
	}

	@Test
	void validacaoApontaQualCampoEstaErrado() throws Exception {
		mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
			.content("{\"defaultTier\":\"low\",\"defaultJavaVersion\":100,\"language\":\"pt-BR\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("defaultJavaVersion"));
	}
}
