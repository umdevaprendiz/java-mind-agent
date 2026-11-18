package com.example.java_mind_agent.models.ollama;

import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.FinishReason;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Thinking;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Token;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Usage;
import com.example.java_mind_agent.models.routing.ModelRouter;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.support.OpenApiContract;

import reactor.core.publisher.Flux;

@WebMvcTest(OllamaStreamController.class)
@EnableConfigurationProperties(AppProperties.class) // o WebConfig precisa dele; o slice não varre @ConfigurationProperties
@TestPropertySource(properties = "app.models.debug.endpoint-enabled=true")
class OllamaStreamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OllamaChatService chat;

	@MockitoBean
	private ModelRouter router;

	@Test
	void devolveTokensUsoEFimComOFormatoDoContrato() throws Exception {
		when(chat.stream(eq("m"), any(), any())).thenReturn(Flux.just(new Thinking("hmm"), new Token("Um "),
				new Token("record"), new Completed("m", FinishReason.STOP, new Usage(26, 42, 20.0, 350L, 1000L, 2500))));

		String corpo = enviaESegue("{\"model\":\"m\",\"prompt\":\"O que é um record?\"}");

		assertThat(corpo).contains("event:thinking").contains("\"type\":\"thinking\"").contains("\"text\":\"hmm\"");
		assertThat(corpo).contains("event:token").contains("\"type\":\"token\"").contains("\"text\":\"Um \"");
		assertThat(corpo).contains("event:usage").contains("\"promptTokens\":26").contains("\"completionTokens\":42")
			.contains("\"tokensPerSecond\":20.0").contains("\"timeToFirstTokenMs\":350")
			.contains("\"totalDurationMs\":2500");
		assertThat(corpo).contains("event:done").contains("\"finishReason\":\"stop\"");
		assertThat(corpo.indexOf("event:token")).isLessThan(corpo.indexOf("event:usage"));
		assertThat(corpo.indexOf("event:usage")).isLessThan(corpo.indexOf("event:done"));
	}

	@Test
	void montaAsMensagensEPassaAsOpcoesAoServico() throws Exception {
		when(chat.stream(any(), any(), any())).thenReturn(Flux.empty());

		enviaESegue("""
				{"model":"qwen2.5:1.5b","prompt":"oi","system":"Responda em português.","temperature":0.5,
				 "numCtx":2048,"numPredict":64,"keepAlive":"0","think":false}""");

		ArgumentCaptor<List<Message>> mensagens = ArgumentCaptor.captor();
		ArgumentCaptor<GenerationOptions> opcoes = ArgumentCaptor.captor();
		verify(chat).stream(eq("qwen2.5:1.5b"), mensagens.capture(), opcoes.capture());
		assertThat(mensagens.getValue()).extracting(Message::getMessageType, Message::getText)
			.containsExactly(tuple(MessageType.SYSTEM, "Responda em português."), tuple(MessageType.USER, "oi"));
		assertThat(opcoes.getValue()).isEqualTo(new GenerationOptions(0.5, 2048, 64, "0", false, null, null, null));
	}

	@Test
	void falhaDaGeracaoViraEventoErrorComCodigoDoContrato() throws Exception {
		when(chat.stream(any(), any(), any())).thenReturn(Flux.concat(Flux.just(new Token("Um")), Flux.error(
				new ModelException(ErrorCode.MODEL_NOT_INSTALLED, "O modelo 'x' não está instalado.", false))));

		String corpo = enviaESegue("{\"model\":\"x\",\"prompt\":\"oi\"}");

		assertThat(corpo).contains("event:token").contains("event:error").contains("\"type\":\"error\"")
			.contains("\"code\":\"MODEL_NOT_INSTALLED\"").contains("não está instalado")
			.contains("\"retryable\":false");
	}

	@Test
	void falhaInesperadaViraErroInternoSemVazarDetalhes() throws Exception {
		when(chat.stream(any(), any(), any())).thenReturn(Flux.error(new IllegalStateException("segredo interno")));

		String corpo = enviaESegue("{\"model\":\"x\",\"prompt\":\"oi\"}");

		assertThat(corpo).contains("\"code\":\"INTERNAL_ERROR\"").doesNotContain("segredo interno");
	}

	@Test
	void recusaPedidoSemPergunta() throws Exception {
		mockMvc.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
			.content("{\"model\":\"m\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
			.content("{\"tier\":\"low\",\"prompt\":\"\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void modeloInformadoIgnoraORoteadorEFalaComOOllamaPadrao() throws Exception {
		when(chat.stream(eq("m"), any(), any())).thenReturn(Flux.just(new Token("oi")));

		enviaESegue("{\"model\":\"m\",\"tier\":\"strong\",\"prompt\":\"oi\"}");

		verifyNoInteractions(router);
	}

	// ------------------------------------------------------------------ roteamento por tier (F08)

	private static final Route BAIXO = new Route(Tier.LOW, "Baixo", "qwen2.5:3b", "http://localhost:11434", false,
			false, false);

	@Test
	void semModeloRoteiaPeloTierEAnunciaOModeloEscolhidoAntesDosTokens() throws Exception {
		when(router.resolve(Tier.LOW)).thenReturn(BAIXO);
		when(router.stream(eq(BAIXO), any(), any())).thenReturn(Flux.just(new Token("Um "), new Completed("qwen2.5:3b",
				FinishReason.STOP, new Usage(26, 42, 20.0, 350L, 1000L, 2500))));

		String corpo = enviaESegue("{\"tier\":\"low\",\"prompt\":\"O que é um record?\"}");

		assertThat(corpo).contains("event:route").contains("\"tier\":\"low\"").contains("\"model\":\"qwen2.5:3b\"")
			.contains("\"remote\":false")
			.contains("\"fromDefault\":false");
		assertThat(corpo.indexOf("event:route")).isLessThan(corpo.indexOf("event:token"));
		assertThat(corpo).contains("event:done");
		verifyNoInteractions(chat);
	}

	@Test
	void semModeloENemTierPedeOPadraoAoRoteador() throws Exception {
		Route padrao = new Route(Tier.MEDIUM, "Médio", "qwen2.5:7b", "http://localhost:11434", false, true, true);
		when(router.resolve(null)).thenReturn(padrao);
		when(router.stream(eq(padrao), any(), any())).thenReturn(Flux.empty());

		String corpo = enviaESegue("{\"prompt\":\"oi\"}");

		assertThat(corpo).contains("\"tier\":\"medium\"").contains("\"loaded\":true").contains("\"fromDefault\":true");
		verify(router).resolve(null);
	}

	@Test
	void nivelQueNaoPodeResponderVoltaComoProblemJsonAntesDoStreamComOQueFazer() throws Exception {
		when(router.resolve(Tier.STRONG)).thenThrow(new ModelException(ErrorCode.MODEL_NOT_INSTALLED,
				"O modelo 'qwen2.5:14b' (Forte) não está instalado. Baixe-o antes.", false).detail("tier", "strong")
			.detail("suggestedTier", "medium"));

		String corpo = mockMvc
			.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.content("{\"tier\":\"strong\",\"prompt\":\"oi\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("MODEL_NOT_INSTALLED"))
			.andExpect(jsonPath("$.tier").value("strong"))
			.andExpect(jsonPath("$.suggestedTier").value("medium"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(OpenApiContract.valida("ProblemDetail", corpo)).isEmpty();
		verify(router, never()).stream(any(), any(), any());
	}

	@Test
	void falhaDeMemoriaNoMeioDaGeracaoChegaComoEventoErrorComASugestao() throws Exception {
		when(router.resolve(Tier.MEDIUM)).thenReturn(new Route(Tier.MEDIUM, "Médio", "qwen2.5:7b",
				"http://localhost:11434", false, false, false));
		when(router.stream(any(), any(), any())).thenReturn(Flux.concat(Flux.just(new Token("Um")), Flux.error(
				new ModelException(ErrorCode.MODEL_DOES_NOT_FIT, "Não cabe na memória.", false).detail("tier", "medium")
					.detail("suggestedTier", "low"))));

		String corpo = enviaESegue("{\"tier\":\"medium\",\"prompt\":\"oi\"}");

		String erro = corpo.substring(corpo.indexOf("event:error"));
		assertThat(erro).contains("\"code\":\"MODEL_DOES_NOT_FIT\"").contains("\"tier\":\"medium\"")
			.contains("\"suggestedTier\":\"low\"");
		String dados = erro.substring(erro.indexOf("data:") + 5).lines().findFirst().orElseThrow();
		assertThat(OpenApiContract.valida("ErrorEvent", dados)).isEmpty();
	}

	@Test
	void tierDesconhecidoEBadRequest() throws Exception {
		mockMvc.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
			.content("{\"tier\":\"huge\",\"prompt\":\"oi\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(router);
	}

	private String enviaESegue(String json) throws Exception {
		MvcResult iniciado = mockMvc
			.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.content(json))
			.andExpect(request().asyncStarted())
			.andReturn();
		return mockMvc.perform(asyncDispatch(iniciado))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8); // EventSource do navegador sempre decodifica SSE como UTF-8
	}
}
