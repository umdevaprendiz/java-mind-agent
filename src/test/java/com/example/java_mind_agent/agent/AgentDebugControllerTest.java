package com.example.java_mind_agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.java_mind_agent.agent.AgentEvent.FinishReason;
import com.example.java_mind_agent.agent.AgentEvent.Limit;
import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.agent.loop.LoopExecution;
import com.example.java_mind_agent.common.config.AppProperties;
import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.knowledge.KnowledgeProperties;
import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.knowledge.version.JavaVersionDetector;
import com.example.java_mind_agent.knowledge.version.JavaVersionRegistry;
import com.example.java_mind_agent.knowledge.version.JavaVersionService;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;

import reactor.core.publisher.Flux;

/** O endpoint de depuração do agente: o que entra vira {@link AgentRequest} e o que sai são eventos SSE do contrato. */
@WebMvcTest(AgentDebugController.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.agent.debug.endpoint-enabled=true")
class AgentDebugControllerTest {

	private static final AgentLimits LIMITES = new AgentLimits(6, 10, 4096, Duration.ofMinutes(15),
			Duration.ofSeconds(60), 8000);

	private static final Route ROTA = new Route(Tier.LOW, "Baixo", "qwen2.5:3b", "http://localhost:11434", false, true,
			false, true);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AgentService agente;

	@MockitoBean
	private AgentProperties propriedades;

	private static AgentRun execucao(AgentEvent... eventos) {
		return execucao(Flux.just(eventos));
	}

	private static AgentRun execucao(Flux<AgentEvent> eventos) {
		JavaVersionRegistry registro = new JavaVersionRegistry();
		AssumedVersion versao = new JavaVersionService(registro, new JavaVersionDetector(registro),
				new KnowledgeProperties(null, 8))
			.assume("No Java 17", null, null);
		LoopExecution loop = new LoopExecution() {
			@Override
			public Flux<AgentEvent> events() {
				return eventos;
			}

			@Override
			public void cancel() {
			}
		};
		return new AgentRun(ROTA, versao, true, loop);
	}

	@Test
	void devolveRotaEventosDoContratoEResumoNaOrdem() throws Exception {
		when(propriedades.limits()).thenReturn(LIMITES);
		when(agente.prepare(any())).thenReturn(execucao(
				new AgentEvent.Status(Phase.GENERATING, "Gerando a resposta"),
				new AgentEvent.Token("Um "),
				new AgentEvent.ToolCall("call_1", "java_release_info", Map.of("version", 17)),
				new AgentEvent.ToolResult("call_1", "java_release_info", true, "Java 17 (2021)",
						"Java 17 (2021), LTS", 12),
				new AgentEvent.Usage(30, 10, 5.5, 900L, 4000),
				new AgentEvent.Finished(FinishReason.LENGTH, Limit.TOKENS, 2, 1)));

		String corpo = enviaESegue("{\"message\":\"O que mudou no Java 17?\"}");

		assertThat(corpo).contains("event:route")
			.contains("\"model\":\"qwen2.5:3b\"")
			.contains("\"toolsEnabled\":true")
			.contains("\"javaVersion\":17")
			.contains("\"javaVersionSource\":\"MENTIONED\"");
		assertThat(corpo).contains("event:status").contains("\"phase\":\"generating\"");
		assertThat(corpo).contains("event:token").contains("\"text\":\"Um \"");
		assertThat(corpo).contains("event:tool_call")
			.contains("\"callId\":\"call_1\"")
			.contains("\"tool\":\"java_release_info\"")
			.contains("\"arguments\":{\"version\":17}");
		assertThat(corpo).contains("event:tool_result")
			.contains("\"ok\":true")
			.contains("\"summary\":\"Java 17 (2021)\"")
			.contains("\"durationMs\":12");
		assertThat(corpo).contains("event:usage").contains("\"promptTokens\":30").contains("\"tokensPerSecond\":5.5");
		assertThat(corpo).contains("event:done").contains("\"finishReason\":\"length\"");
		assertThat(corpo).contains("event:summary")
			.contains("\"steps\":2")
			.contains("\"toolCalls\":1")
			.contains("\"limit\":\"TOKENS\"");
		assertThat(corpo.indexOf("event:route")).isLessThan(corpo.indexOf("event:status"));
		assertThat(corpo.indexOf("event:tool_call")).isLessThan(corpo.indexOf("event:tool_result"));
		assertThat(corpo.indexOf("event:usage")).isLessThan(corpo.indexOf("event:done"));
		assertThat(corpo.indexOf("event:done")).isLessThan(corpo.indexOf("event:summary"));
	}

	@Test
	void montaOPedidoComHistoricoLimitesEOpcoes() throws Exception {
		when(propriedades.limits()).thenReturn(LIMITES);
		when(agente.prepare(any())).thenReturn(execucao());

		enviaESegue("""
				{"message":"E no 21?","tier":"strong","javaVersion":21,"language":"en","maxSteps":3,"maxTokens":500,
				 "think":false,"history":[{"role":"user","content":"oi"},{"role":"assistant","content":"olá"}]}""");

		ArgumentCaptor<AgentRequest> pedido = ArgumentCaptor.captor();
		verify(agente).prepare(pedido.capture());
		AgentRequest r = pedido.getValue();
		assertThat(r.message()).isEqualTo("E no 21?");
		assertThat(r.tier()).isEqualTo(Tier.STRONG);
		assertThat(r.javaVersion()).isEqualTo(21);
		assertThat(r.language()).isEqualTo(Language.EN);
		assertThat(r.history()).containsExactly(new UserMessage("oi"), new AssistantMessage("olá"));
		assertThat(r.limits())
			.isEqualTo(new AgentLimits(3, 10, 500, Duration.ofMinutes(15), Duration.ofSeconds(60), 8000));
		assertThat(r.generation().think()).isFalse();
	}

	@Test
	void semMaxStepsNemMaxTokensValemOsLimitesDaConfiguracao() throws Exception {
		when(propriedades.limits()).thenReturn(LIMITES);
		when(agente.prepare(any())).thenReturn(execucao());

		enviaESegue("{\"message\":\"oi\"}");

		ArgumentCaptor<AgentRequest> pedido = ArgumentCaptor.captor();
		verify(agente).prepare(pedido.capture());
		assertThat(pedido.getValue().limits()).isEqualTo(LIMITES);
		assertThat(pedido.getValue().generation()).isNull();
	}

	@Test
	void falhaDuranteAGeracaoViraEventoErrorComCodigoDoContrato() throws Exception {
		when(propriedades.limits()).thenReturn(LIMITES);
		when(agente.prepare(any())).thenReturn(execucao(Flux.concat(Flux.just(new AgentEvent.Token("Um")),
				Flux.error(new ModelException(ErrorCode.GENERATION_TIMEOUT, "demorou", true)))));

		String corpo = enviaESegue("{\"message\":\"oi\"}");

		assertThat(corpo).contains("event:token")
			.contains("event:error")
			.contains("\"code\":\"GENERATION_TIMEOUT\"")
			.contains("\"retryable\":true");
	}

	@Test
	void falhaAntesDoStreamSaiComoProblemJson() throws Exception {
		when(propriedades.limits()).thenReturn(LIMITES);
		when(agente.prepare(any()))
			.thenThrow(new ModelException(ErrorCode.MODEL_NOT_INSTALLED, "O modelo não está instalado.", false));

		mockMvc
			.perform(post("/internal/agent/run").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON)
				.content("{\"message\":\"oi\"}"))
			.andExpect(status().is(ErrorCode.MODEL_NOT_INSTALLED.httpStatus()));
	}

	@Test
	void recusaPedidoSemPergunta() throws Exception {
		mockMvc
			.perform(post("/internal/agent/run").contentType(MediaType.APPLICATION_JSON).content("{\"tier\":\"low\"}"))
			.andExpect(status().isBadRequest());
		mockMvc
			.perform(post("/internal/agent/run").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
			.andExpect(status().isBadRequest());
	}

	private String enviaESegue(String json) throws Exception {
		MvcResult iniciado = mockMvc
			.perform(post("/internal/agent/run").contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.content(json))
			.andExpect(request().asyncStarted())
			.andReturn();
		return mockMvc.perform(asyncDispatch(iniciado))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
	}
}
