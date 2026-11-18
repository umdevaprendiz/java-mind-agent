package com.example.java_mind_agent.models.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.support.OpenApiContract;

import reactor.core.publisher.Flux;

/**
 * Os cinco endpoints do gerenciador, o formato SSE do download contra o contrato ({@code ModelPullStreamEvent}) e o
 * {@code problem+json} de cada falha anterior ao stream (que também vale para o handler global de erros). A regra de
 * cada operação está em {@link ModelManagerServiceTest}.
 */
@WebMvcTest(ModelManagerController.class)
@EnableConfigurationProperties(AppProperties.class) // o WebConfig precisa dele; o slice não varre @ConfigurationProperties
class ModelManagerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ModelManagerService manager;

	// ================================================================== download

	@Test
	void downloadEmitePorgressoEFimEOsEventosCumpremOContrato() throws Exception {
		when(manager.pull("strong")).thenReturn(Flux.just(new PullEvent.Progress("pulling manifest", null, null, null),
				new PullEvent.Progress("downloading", 1_073_741_824L, 4_683_087_332L, 22.9), new PullEvent.Done()));

		String corpo = baixa("strong");

		List<String[]> eventos = eventos(corpo);
		assertThat(eventos).extracting(e -> e[0]).containsExactly("progress", "progress", "done");
		assertThat(eventos.get(0)[1]).contains("\"type\":\"progress\"")
			.contains("\"status\":\"pulling manifest\"")
			.contains("\"completedBytes\":null")
			.contains("\"percent\":null");
		assertThat(eventos.get(1)[1]).contains("\"completedBytes\":1073741824")
			.contains("\"totalBytes\":4683087332")
			.contains("\"percent\":22.9");
		assertThat(eventos.get(2)[1]).isEqualTo("{\"type\":\"done\"}");
		for (String[] evento : eventos) {
			assertThat(OpenApiContract.valida("ModelPullStreamEvent", evento[1])).as(evento[1]).isEmpty();
		}
		verify(manager).pull("strong");
	}

	@Test
	void falhaNoMeioDoDownloadViraEventoErrorNoStreamQueJaEstavaAbertoComOk() throws Exception {
		when(manager.pull("strong")).thenReturn(Flux.concat(
				Flux.just(new PullEvent.Progress("downloading", 10L, 100L, 10.0)),
				Flux.error(new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
						"A conexão caiu durante o download. Baixe de novo: o download continua de onde parou.", true))));

		List<String[]> eventos = eventos(baixa("strong"));

		assertThat(eventos).extracting(e -> e[0]).containsExactly("progress", "error");
		assertThat(eventos.get(1)[1]).contains("\"type\":\"error\"")
			.contains("\"code\":\"OLLAMA_UNAVAILABLE\"")
			.contains("\"retryable\":true")
			.contains("continua de onde parou");
		assertThat(OpenApiContract.valida("ModelPullStreamEvent", eventos.get(1)[1])).isEmpty();
	}

	@Test
	void falhaInesperadaNoDownloadNaoVazaDetalhesInternos() throws Exception {
		when(manager.pull("low")).thenReturn(Flux.error(new IllegalStateException("segredo interno")));

		String corpo = baixa("low");

		assertThat(corpo).contains("\"code\":\"INTERNAL_ERROR\"").doesNotContain("segredo interno");
	}

	// ============================================== erros antes de o stream começar

	static Stream<Arguments> falhasAntesDoStream() {
		return Stream.of(
				Arguments.of(ErrorCode.NOT_FOUND, 404, "O modelo 'huge' não existe. Use low, medium, strong, extra ou embedding.", false),
				Arguments.of(ErrorCode.CONFLICT, 409, "O modelo 'qwen2.5:14b' já está sendo baixado.", false),
				Arguments.of(ErrorCode.MODEL_DOES_NOT_FIT, 422, "O modelo 'qwen3:32b' não cabe nesta máquina.", false),
				Arguments.of(ErrorCode.MODEL_NOT_INSTALLED, 422, "O modelo 'qwen2.5:14b' não está instalado.", false),
				Arguments.of(ErrorCode.OLLAMA_UNAVAILABLE, 503, "Não consegui falar com o Ollama.", true));
	}

	@ParameterizedTest(name = "{0} vira HTTP {1}")
	@MethodSource("falhasAntesDoStream")
	void downloadRecusadoAntesDeComecarVoltaComoProblemJsonMesmoPedindoEventStream(ErrorCode code, int http,
			String mensagem, boolean retryable) throws Exception {
		when(manager.pull(anyString())).thenThrow(new ModelException(code, mensagem, retryable));

		String corpo = mockMvc
			.perform(post("/api/models/strong/pull").accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().is(http))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value(code.name()))
			.andExpect(jsonPath("$.status").value(http))
			.andExpect(jsonPath("$.detail").value(mensagem))
			.andExpect(jsonPath("$.retryable").value(retryable))
			.andExpect(jsonPath("$.instance").value("/api/models/strong/pull"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
	}

	// ============================================== remover, carregar, descarregar

	@Test
	void removerDevolve204SemCorpo() throws Exception {
		mockMvc.perform(delete("/api/models/low")).andExpect(status().isNoContent()).andExpect(content().string(""));

		verify(manager).delete("low");
	}

	@Test
	void carregarDevolve202SemEsperarOCarregamento() throws Exception {
		mockMvc.perform(post("/api/models/medium/warmup")).andExpect(status().isAccepted()).andExpect(content().string(""));

		verify(manager).warmUp("medium");
	}

	@Test
	void descarregarDevolve204() throws Exception {
		mockMvc.perform(post("/api/models/embedding/unload")).andExpect(status().isNoContent());

		verify(manager).unload("embedding");
	}

	@Test
	void removerModeloQueNaoEstaInstaladoEhNotFoundComProblemJson() throws Exception {
		doThrow(new ModelException(ErrorCode.NOT_FOUND, "O modelo 'qwen2.5:14b' não está instalado.", false))
			.when(manager)
			.delete("strong");

		String corpo = mockMvc.perform(delete("/api/models/strong"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.title").value("Not Found"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
	}

	@Test
	void removerEnquantoBaixaEhConflito() throws Exception {
		doThrow(new ModelException(ErrorCode.CONFLICT, "O modelo está sendo baixado.", false)).when(manager)
			.delete("strong");

		mockMvc.perform(delete("/api/models/strong"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.retryable").value(false));
	}

	@Test
	void carregarModeloNaoInstaladoEhUnprocessableComCodigoParaOFrontOferecerBaixar() throws Exception {
		doThrow(new ModelException(ErrorCode.MODEL_NOT_INSTALLED, "Baixe-o antes.", false)).when(manager)
			.warmUp("strong");

		String corpo = mockMvc.perform(post("/api/models/strong/warmup"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MODEL_NOT_INSTALLED"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
	}

	@Test
	void descarregarSemOllamaEh503ComRetryable() throws Exception {
		doThrow(new ModelException(ErrorCode.OLLAMA_UNAVAILABLE, "Ollama fora do ar.", true)).when(manager)
			.unload("low");

		mockMvc.perform(post("/api/models/low/unload"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("OLLAMA_UNAVAILABLE"))
			.andExpect(jsonPath("$.retryable").value(true));
	}

	// ====================================================== handler global de erros

	@Test
	void falhaInesperadaVira500SemVazarAMensagemInternaNemOStackTrace() throws Exception {
		doThrow(new IllegalStateException("senha=123 em /var/segredo")).when(manager).delete("low");

		String corpo = mockMvc.perform(delete("/api/models/low"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(corpo).doesNotContain("senha").doesNotContain("segredo").doesNotContain("IllegalStateException");
		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
	}

	@Test
	void rotaInexistenteTambemVoltaComoProblemJsonComCodigo() throws Exception {
		String corpo = mockMvc.perform(get("/api/nao-existe"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
	}

	@Test
	void metodoErradoVira405EmProblemJson() throws Exception {
		String corpo = mockMvc.perform(get("/api/models/low/pull"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
	}

	// ================================================================== auxiliares

	private String baixa(String modelId) throws Exception {
		MvcResult iniciado = mockMvc
			.perform(post("/api/models/{id}/pull", modelId).accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(request().asyncStarted())
			.andReturn();
		return mockMvc.perform(asyncDispatch(iniciado))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8); // EventSource do navegador sempre decodifica SSE como UTF-8
	}

	/** Separa o corpo SSE em pares {nome do evento, JSON do data}; comentários de keep-alive ficam de fora. */
	private static List<String[]> eventos(String corpo) {
		List<String[]> eventos = new ArrayList<>();
		for (String bloco : corpo.split("\n\n")) {
			String nome = null;
			String dados = null;
			for (String linha : bloco.split("\n")) {
				if (linha.startsWith("event:")) {
					nome = linha.substring("event:".length()).trim();
				}
				else if (linha.startsWith("data:")) {
					dados = linha.substring("data:".length()).trim();
				}
			}
			if (nome != null && dados != null) {
				eventos.add(new String[] { nome, dados });
			}
		}
		return eventos;
	}
}
