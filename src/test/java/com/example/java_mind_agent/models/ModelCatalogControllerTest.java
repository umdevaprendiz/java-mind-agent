package com.example.java_mind_agent.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.models.ModelInfo.Kind;
import com.example.java_mind_agent.models.ModelInfo.UnavailableReason;
import com.example.java_mind_agent.support.OpenApiContract;

/**
 * {@code GET /api/models}: o JSON que sai do controller tem de cumprir o schema {@code ModelCatalog} do contrato, que é
 * o que o front lê. A montagem do catálogo em si está em {@link ModelCatalogServiceTest}.
 */
@WebMvcTest(ModelCatalogController.class)
@EnableConfigurationProperties(AppProperties.class) // o WebConfig precisa dele; o slice não varre @ConfigurationProperties
class ModelCatalogControllerTest {

	private static final HardwareInfo MAQUINA = new HardwareInfo(15.76, 2.5, "Intel(R) UHD Graphics", true);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ModelCatalogService service;

	@Test
	void devolveOCatalogoNoFormatoDoContrato() throws Exception {
		when(service.catalog()).thenReturn(catalogoDaMaquinaDeDesenvolvimento());

		String corpo = mockMvc.perform(get("/api/models").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.hardware.totalRamGb").value(15.76))
			.andExpect(jsonPath("$.hardware.gpu").value("Intel(R) UHD Graphics"))
			.andExpect(jsonPath("$.hardware.cpuOnly").value(true))
			.andExpect(jsonPath("$.defaultTier").value("medium"))
			.andExpect(jsonPath("$.tiers.length()").value(4))
			.andExpect(jsonPath("$.tiers[0].id").value("low"))
			.andExpect(jsonPath("$.tiers[1].speed").value("slow"))
			.andExpect(jsonPath("$.tiers[1].recommended").value(true))
			.andExpect(jsonPath("$.tiers[2].unavailableReason").value("NOT_INSTALLED"))
			.andExpect(jsonPath("$.tiers[3].unavailableReason").value("DOES_NOT_FIT"))
			.andExpect(jsonPath("$.tiers[3].remoteHost").value("http://gpu-server:11434"))
			.andExpect(jsonPath("$.embedding.id").value("embedding"))
			.andExpect(jsonPath("$.embedding.kind").value("embedding"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ModelCatalog", corpo)).isEmpty();
	}

	@Test
	void comOOllamaForaDoArRespondeOkEOCatalogoAindaCumpreOContrato() throws Exception {
		when(service.catalog()).thenReturn(catalogoSemOllama());

		String corpo = mockMvc.perform(get("/api/models"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hardware.totalRamGb").value(15.76))
			.andExpect(jsonPath("$.tiers[0].unavailableReason").value("HOST_UNREACHABLE"))
			.andExpect(jsonPath("$.tiers[0].available").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ModelCatalog", corpo)).isEmpty();
	}

	@Test
	void oValidadorDoContratoRejeitaCatalogoSemOsQuatroCamposObrigatorios() {
		assertThat(OpenApiContract.valida("ModelCatalog", "{\"tiers\":[]}")).isNotEmpty();
	}

	// ------------------------------------------------------------------ dados

	private static ModelCatalog catalogoDaMaquinaDeDesenvolvimento() {
		return new ModelCatalog(MAQUINA, Tier.MEDIUM, List.of(
				new ModelInfo("low", Kind.CHAT, "Baixo", "Respostas rápidas.", "qwen2.5:3b", null, 1.77, 2.5, true,
						false, true, false, true, null, false, Speed.FAST, null, 1.77),
				new ModelInfo("medium", Kind.CHAT, "Médio", "Uso geral.", "qwen2.5:7b", null, 4.36, 6, true, true,
						true, true, true, null, true, Speed.SLOW, null, 4.36),
				new ModelInfo("strong", Kind.CHAT, "Forte", "Mais qualidade, bem mais lento.", "qwen2.5:14b", null,
						null, 12, false, false, true, null, false, UnavailableReason.NOT_INSTALLED, false,
						Speed.VERY_SLOW, null, null),
				new ModelInfo("extra", Kind.CHAT, "Extra", "O mais forte; precisa de outro computador.", "qwen3:32b",
						"http://gpu-server:11434", null, 26, false, false, false, null, false,
						UnavailableReason.DOES_NOT_FIT, false, Speed.VERY_SLOW, null, null)),
				new ModelInfo("embedding", Kind.EMBEDDING, "Embeddings", "Vetores para a busca.", "bge-m3", null, 1.08,
						1.5, true, false, true, null, true, null, true, Speed.FAST, null, 1.08));
	}

	private static ModelCatalog catalogoSemOllama() {
		List<ModelInfo> tiers = List.of(
				fora("low", "Baixo", "qwen2.5:3b", 2.5, true, Speed.FAST),
				fora("medium", "Médio", "qwen2.5:7b", 6, true, Speed.SLOW),
				fora("strong", "Forte", "qwen2.5:14b", 12, true, Speed.VERY_SLOW),
				fora("extra", "Extra", "qwen3:32b", 26, false, Speed.VERY_SLOW));
		return new ModelCatalog(MAQUINA, Tier.MEDIUM, tiers,
				new ModelInfo("embedding", Kind.EMBEDDING, "Embeddings", "Vetores para a busca.", "bge-m3", null, null,
						1.5, false, false, true, null, false, UnavailableReason.HOST_UNREACHABLE, true, Speed.FAST, null,
						null));
	}

	private static ModelInfo fora(String id, String label, String modelo, double minRamGb, boolean cabe, Speed speed) {
		return new ModelInfo(id, Kind.CHAT, label, "Descrição.", modelo, null, null, minRamGb, false, false, cabe, null,
				false, UnavailableReason.HOST_UNREACHABLE, false, speed, null, null);
	}
}
