package com.example.java_mind_agent.models;

import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.example.java_mind_agent.common.ErrorCode;
import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.models.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ModelStreamEvent.FinishReason;
import com.example.java_mind_agent.models.ModelStreamEvent.Thinking;
import com.example.java_mind_agent.models.ModelStreamEvent.Token;
import com.example.java_mind_agent.models.ModelStreamEvent.Usage;

import reactor.core.publisher.Flux;

@WebMvcTest(OllamaStreamController.class)
@EnableConfigurationProperties(AppProperties.class) // o WebConfig precisa dele; o slice não varre @ConfigurationProperties
@TestPropertySource(properties = "app.models.debug.endpoint-enabled=true")
class OllamaStreamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OllamaChatService chat;

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
	void recusaPedidoSemModeloOuSemPergunta() throws Exception {
		mockMvc.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
			.content("{\"model\":\"\",\"prompt\":\"oi\"}")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/internal/models/stream").contentType(MediaType.APPLICATION_JSON)
			.content("{\"model\":\"m\"}")).andExpect(status().isBadRequest());
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
