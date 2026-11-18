package com.example.java_mind_agent.agent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import com.example.java_mind_agent.agent.AgentEvent;
import com.example.java_mind_agent.agent.AgentEvent.FinishReason;
import com.example.java_mind_agent.agent.AgentEvent.Finished;
import com.example.java_mind_agent.agent.AgentEvent.Limit;
import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.agent.AgentEvent.Status;
import com.example.java_mind_agent.agent.AgentEvent.ToolCall;
import com.example.java_mind_agent.agent.AgentEvent.ToolResult;
import com.example.java_mind_agent.agent.AgentEvent.Usage;
import com.example.java_mind_agent.agent.AgentLimits;
import com.example.java_mind_agent.agent.FerramentasDeTeste;
import com.example.java_mind_agent.agent.tool.ToolExecutor;
import com.example.java_mind_agent.agent.tool.ToolRegistry;
import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Completed;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCallRequest;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.ToolCalls;
import com.example.java_mind_agent.models.routing.ModelRouter;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

/**
 * O loop do agente com o modelo roteirizado: cada chamada ao modelo recebe a próxima resposta do roteiro, e o teste
 * confere o que o loop emitiu, o que ele mandou ao modelo e como respeitou os limites. Sem Ollama, sem Spring.
 */
class AgentLoopTest {

	private static final Route ROTA = new Route(Tier.MEDIUM, "Médio", "qwen2.5:7b", "http://localhost:11434", false,
			true, false, true);

	/** Uma chamada que o loop fez ao modelo. */
	private record Chamada(List<Message> mensagens, GenerationOptions opcoes, List<ToolCallback> ferramentas) {
	}

	private final Deque<Supplier<Flux<ModelStreamEvent>>> roteiro = new ArrayDeque<>();

	private final List<Chamada> chamadas = Collections.synchronizedList(new ArrayList<>());

	private final FerramentasDeTeste ferramentas = new FerramentasDeTeste();

	private ModelRouter router;

	private AgentLoop loop;

	@BeforeEach
	void preparaLoop() {
		router = mock(ModelRouter.class);
		when(router.stream(any(Route.class), anyList(), any(GenerationOptions.class), anyList())).thenAnswer(invocacao -> {
			chamadas.add(new Chamada(List.copyOf(invocacao.<List<Message>>getArgument(1)), invocacao.getArgument(2),
					List.copyOf(invocacao.<List<ToolCallback>>getArgument(3))));
			assertThat(roteiro).as("o loop chamou o modelo mais vezes do que o roteiro prevê").isNotEmpty();
			return roteiro.removeFirst().get();
		});
		JsonMapper json = JsonMapper.builder().build();
		loop = new AgentLoop(router, new ToolRegistry(List.of(ferramentas)), new ToolExecutor(json), json);
	}

	// -------------------------------------------------------------------- pergunta simples

	@Test
	void perguntaSimplesStreamaOsTokensEFechaComUsoEFim() {
		responde(token("Um "), token("record "), fim(26, 42, 20.0));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		assertThat(eventos).containsExactly(new Status(Phase.GENERATING, "Gerando a resposta"),
				new AgentEvent.Token("Um "), new AgentEvent.Token("record "), uso(eventos), new Finished(FinishReason.STOP, null, 1, 0));
		Usage uso = uso(eventos);
		assertThat(uso.promptTokens()).isEqualTo(26);
		assertThat(uso.completionTokens()).isEqualTo(42);
		assertThat(uso.tokensPerSecond()).isCloseTo(20.0, within(0.001));
		assertThat(uso.timeToFirstTokenMs()).isNotNull();
		assertThat(chamadas).hasSize(1);
		assertThat(chamadas.getFirst().ferramentas()).as("o modelo recebe as ferramentas").hasSize(5);
	}

	@Test
	void raciocinioDoModeloViraThinkingSemEntrarNaResposta() {
		responde(new ModelStreamEvent.Thinking("hmm"), token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		assertThat(eventos).contains(new AgentEvent.Thinking("hmm"), new AgentEvent.Token("ok"));
	}

	@Test
	void modeloAindaForaDaMemoriaAvisaQueEstaCarregando() {
		Route fria = new Route(Tier.MEDIUM, "Médio", "qwen2.5:7b", "http://localhost:11434", false, false, false, true);
		responde(token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(
				loop.start(entrada(fria, limites(), true, null)));

		assertThat(eventos.getFirst()).isEqualTo(new Status(Phase.LOADING_MODEL, "Carregando o modelo na memória"));
		assertThat(eventos.get(1)).isEqualTo(new Status(Phase.GENERATING, "Gerando a resposta"));
	}

	@Test
	void aAberturaDaRespostaSaiPrimeiroSemEsperarOModelo() {
		responde(token("Um record"), fim(1, 1, null));
		String abertura = "Versão do Java assumida: Java 25.\n\n";

		List<AgentEvent> eventos = coleta(loop.start(new LoopInput(ROTA, mensagens(), true, null, abertura, limites(),
				GenerationOptions.defaults(), Language.PT_BR)));

		assertThat(eventos.getFirst()).isEqualTo(new AgentEvent.Token(abertura));
		assertThat(eventos).containsSubsequence(new AgentEvent.Token(abertura),
				new Status(Phase.GENERATING, "Gerando a resposta"), new AgentEvent.Token("Um record"));
		assertThat(chamadas.getFirst().mensagens()).as("o modelo não vê a abertura").hasSize(2);
	}

	@Test
	void aAberturaVemAntesDoAvisoDeCarregarOModelo() {
		Route fria = new Route(Tier.MEDIUM, "Médio", "qwen2.5:7b", "http://localhost:11434", false, false, false, true);
		responde(token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(loop.start(new LoopInput(fria, mensagens(), true, null, "Abertura\n\n",
				limites(), GenerationOptions.defaults(), Language.PT_BR)));

		assertThat(eventos.get(0)).isEqualTo(new AgentEvent.Token("Abertura\n\n"));
		assertThat(eventos.get(1)).isEqualTo(new Status(Phase.LOADING_MODEL, "Carregando o modelo na memória"));
	}

	// ------------------------------------------------------------------------ ferramentas

	@Test
	void executaAFerramentaPedidaEDevolveOResultadoAoModelo() {
		responde(token("Vou repetir. "), pede("eco", "{\"texto\":\"oi\"}"), fim(30, 10, 10.0));
		responde(token("Saiu OI."), fim(60, 8, 10.0));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		assertThat(eventos).containsSubsequence(
				new AgentEvent.Token("Vou repetir. "),
				new Status(Phase.EXECUTING_CODE, "Usando a ferramenta eco"),
				new ToolCall("call_1", "eco", Map.of("texto", "oi")),
				new Status(Phase.GENERATING, "Analisando o resultado das ferramentas (passo 2)"),
				new AgentEvent.Token("Saiu OI."));
		ToolResult resultado = (ToolResult) eventos.stream().filter(ToolResult.class::isInstance).findFirst().orElseThrow();
		assertThat(resultado.callId()).isEqualTo("call_1");
		assertThat(resultado.tool()).isEqualTo("eco");
		assertThat(resultado.ok()).isTrue();
		assertThat(resultado.output()).isEqualTo("OI");
		assertThat(resultado.summary()).isEqualTo("OI");
		assertThat(ferramentas.textosRecebidos).containsExactly("oi");

		// o histórico do segundo passo: pergunta, o que o modelo disse e pediu, o resultado da ferramenta
		List<Message> historico = chamadas.get(1).mensagens();
		assertThat(historico).hasSize(4);
		assertThat(historico.get(1)).isInstanceOf(UserMessage.class);
		AssistantMessage pedido = (AssistantMessage) historico.get(2);
		assertThat(pedido.getText()).isEqualTo("Vou repetir. ");
		assertThat(pedido.getToolCalls()).singleElement().satisfies(chamada -> {
			assertThat(chamada.id()).isEqualTo("call_1");
			assertThat(chamada.name()).isEqualTo("eco");
			assertThat(chamada.arguments()).isEqualTo("{\"texto\":\"oi\"}");
		});
		ToolResponseMessage resposta = (ToolResponseMessage) historico.get(3);
		assertThat(resposta.getResponses()).singleElement().satisfies(r -> {
			assertThat(r.id()).isEqualTo("call_1");
			assertThat(r.name()).isEqualTo("eco");
			assertThat(r.responseData()).isEqualTo("OI");
		});

		Finished fim = (Finished) eventos.getLast();
		assertThat(fim).isEqualTo(new Finished(FinishReason.STOP, null, 2, 1));
		Usage uso = uso(eventos);
		assertThat(uso.promptTokens()).as("soma dos dois passos").isEqualTo(90);
		assertThat(uso.completionTokens()).isEqualTo(18);
	}

	@Test
	void executaVariasFerramentasDeUmPassoNaOrdemEmQueForamPedidas() {
		responde(pede("eco", "{\"texto\":\"a\"}", "eco", "{\"texto\":\"b\"}"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		assertThat(eventos.stream().filter(ToolCall.class::isInstance).map(e -> ((ToolCall) e).callId()))
			.containsExactly("call_1", "call_2");
		assertThat(eventos.stream().filter(ToolResult.class::isInstance).map(e -> ((ToolResult) e).output()))
			.containsExactly("A", "B");
		ToolResponseMessage respostas = (ToolResponseMessage) chamadas.get(1).mensagens().getLast();
		assertThat(respostas.getResponses()).extracting(ToolResponseMessage.ToolResponse::responseData)
			.containsExactly("A", "B");
	}

	@Test
	void ferramentaInexistenteViraErroQueOModeloLe() {
		responde(pede("nao_existe", "{}"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		ToolResult resultado = resultadoDe(eventos);
		assertThat(resultado.ok()).isFalse();
		assertThat(resultado.output()).contains("'nao_existe' não existe").contains("eco").contains("quebra");
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, null, 2, 1));
	}

	@Test
	void ferramentaQueLancaExcecaoNaoDerrubaOLoop() {
		responde(pede("quebra", "{}"), fim(1, 1, null));
		responde(token("Desculpe."), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		ToolResult resultado = resultadoDe(eventos);
		assertThat(resultado.ok()).isFalse();
		assertThat(resultado.output()).startsWith("A ferramenta falhou:").contains("explodiu de propósito");
		ToolResponseMessage voltou = (ToolResponseMessage) chamadas.get(1).mensagens().getLast();
		assertThat(voltou.getResponses().getFirst().responseData()).contains("explodiu de propósito");
		assertThat(eventos.getLast()).isInstanceOf(Finished.class);
	}

	@Test
	void argumentosQueNaoSaoJsonViramErroEOEventoVaiComArgumentosVazios() {
		responde(pede("eco", "isto não é json"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		ToolCall chamada = (ToolCall) eventos.stream().filter(ToolCall.class::isInstance).findFirst().orElseThrow();
		assertThat(chamada.arguments()).isEmpty();
		assertThat(resultadoDe(eventos).ok()).isFalse();
		assertThat(ferramentas.execucoes).hasValue(0);
	}

	@Test
	void ferramentaQueDemoraDemaisEstouraOTempoEVoltaComoErro() {
		responde(pede("lenta", "{\"ms\":5000}"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));
		AgentLimits curto = new AgentLimits(6, 10, 4096, Duration.ofMinutes(1), Duration.ofMillis(200), 8000);

		List<AgentEvent> eventos = coleta(execucao(curto, true));

		ToolResult resultado = resultadoDe(eventos);
		assertThat(resultado.ok()).isFalse();
		assertThat(resultado.output()).contains("passou de 200 ms");
		assertThat(resultado.durationMs()).isLessThan(3000);
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, null, 2, 1));
	}

	@Test
	void saidaGrandeDeFerramentaEhCortadaParaOModeloEParaATela() {
		responde(pede("grande", "{}"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));
		AgentLimits curto = new AgentLimits(6, 10, 4096, Duration.ofMinutes(1), Duration.ofSeconds(5), 40);

		List<AgentEvent> eventos = coleta(execucao(curto, true));

		ToolResult resultado = resultadoDe(eventos);
		assertThat(resultado.output()).startsWith("x".repeat(40)).contains("saída cortada em 40 caracteres");
		ToolResponseMessage voltou = (ToolResponseMessage) chamadas.get(1).mensagens().getLast();
		assertThat(voltou.getResponses().getFirst().responseData()).isEqualTo(resultado.output());
	}

	@Test
	void saidaVaziaViraUmAvisoParaOModeloNaoFicarSemResposta() {
		responde(pede("vazia", "{}"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));

		ToolResult resultado = resultadoDe(coleta(execucao(limites(), true)));

		assertThat(resultado.ok()).isTrue();
		assertThat(resultado.output()).isEqualTo("(sem saída)");
	}

	// ------------------------------------------------------- modelo sem suporte a ferramentas

	@Test
	void semSuporteAFerramentasONaoOferecemEOAvisoApareceNoComeco() {
		responde(token("ok"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(loop.start(entrada(ROTA, limites(), false, "O modelo x não usa ferramentas")));

		assertThat(eventos.getFirst()).isEqualTo(new Status(Phase.GENERATING, "O modelo x não usa ferramentas"));
		assertThat(chamadas.getFirst().ferramentas()).isEmpty();
	}

	@Test
	void pedidoDeFerramentaQueNaoFoiOferecidaEhIgnoradoEARespostaTermina() {
		responde(token("Resposta."), pede("eco", "{\"texto\":\"a\"}"), fim(1, 1, null));

		List<AgentEvent> eventos = coleta(loop.start(entrada(ROTA, limites(), false, null)));

		assertThat(eventos).noneMatch(ToolCall.class::isInstance);
		assertThat(ferramentas.execucoes).hasValue(0);
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, null, 1, 0));
	}

	// ------------------------------------------------------------------------------ limites

	@Test
	void noUltimoPassoAsFerramentasSaemEOModeloEMandadoFecharARespostaComOQueTem() {
		responde(pede("eco", "{\"texto\":\"a\"}"), fim(1, 1, null));
		responde(token("Fechando."), fim(1, 1, null));
		AgentLimits doisPassos = new AgentLimits(2, 10, 4096, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		List<AgentEvent> eventos = coleta(execucao(doisPassos, true));

		assertThat(chamadas.get(0).ferramentas()).isNotEmpty();
		assertThat(chamadas.get(1).ferramentas()).as("último passo não oferece ferramentas").isEmpty();
		Message ultima = chamadas.get(1).mensagens().getLast();
		assertThat(ultima).isInstanceOf(UserMessage.class);
		assertThat(ultima.getText()).contains("não pode mais chamar ferramentas");
		assertThat(eventos).contains(new Status(Phase.GENERATING, "Limite de 2 passos atingido: respondo com o que já obtive."));
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, Limit.STEPS, 2, 1));
	}

	@Test
	void comUmPassoSoOAgenteNuncaUsaFerramentas() {
		responde(token("ok"), fim(1, 1, null));
		AgentLimits umPasso = new AgentLimits(1, 10, 4096, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		coleta(execucao(umPasso, true));

		assertThat(chamadas.getFirst().ferramentas()).isEmpty();
	}

	@Test
	void modeloQueAindaPedeFerramentaNoUltimoPassoTemOPedidoIgnorado() {
		responde(pede("eco", "{\"texto\":\"a\"}"), fim(1, 1, null));
		responde(pede("eco", "{\"texto\":\"b\"}"), fim(1, 1, null));
		responde(token("fim"), pede("eco", "{\"texto\":\"c\"}"), fim(1, 1, null));
		AgentLimits tresPassos = new AgentLimits(3, 10, 4096, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		List<AgentEvent> eventos = coleta(execucao(tresPassos, true));

		assertThat(ferramentas.execucoes).as("só os dois primeiros passos executam").hasValue(2);
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, Limit.STEPS, 3, 2));
	}

	@Test
	void ferramentasAlemDoLimiteDeChamadasVoltamComoErroSemExecutar() {
		responde(pede("eco", "{\"texto\":\"a\"}", "eco", "{\"texto\":\"b\"}"), fim(1, 1, null));
		responde(token("ok"), fim(1, 1, null));
		AgentLimits umaChamada = new AgentLimits(6, 1, 4096, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		List<AgentEvent> eventos = coleta(execucao(umaChamada, true));

		List<ToolResult> resultados = eventos.stream().filter(ToolResult.class::isInstance).map(ToolResult.class::cast).toList();
		assertThat(resultados).extracting(ToolResult::ok).containsExactly(true, false);
		assertThat(resultados.get(1).output()).contains("Limite de 1 chamadas de ferramenta atingido");
		assertThat(ferramentas.execucoes).hasValue(1);
		assertThat(chamadas.get(1).ferramentas()).as("sem orçamento de chamadas, o modelo só responde").isEmpty();
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.STOP, null, 2, 1));
	}

	@Test
	void orcamentoDeTokensViraOTetoDeCadaChamada() {
		responde(pede("eco", "{\"texto\":\"a\"}"), fim(10, 30, null));
		responde(token("ok"), fim(10, 10, null));
		AgentLimits pouco = new AgentLimits(6, 10, 100, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		coleta(execucao(pouco, true));

		assertThat(chamadas.get(0).opcoes().numPredict()).isEqualTo(100);
		assertThat(chamadas.get(1).opcoes().numPredict()).as("100 do orçamento - 30 já gastos").isEqualTo(70);
	}

	@Test
	void limiteDeTokensPorChamadaDoPedidoNaoEhAumentadoPeloOrcamento() {
		responde(token("ok"), fim(1, 1, null));
		GenerationOptions menor = GenerationOptions.defaults().withNumPredict(50);

		coleta(loop.start(new LoopInput(ROTA, mensagens(), true, null, null, limites(), menor, Language.PT_BR)));

		assertThat(chamadas.getFirst().opcoes().numPredict()).isEqualTo(50);
	}

	@Test
	void orcamentoDeTokensGastoNosPassosDeFerramentaTerminaSemChamarOModeloDeNovo() {
		responde(pede("eco", "{\"texto\":\"a\"}"), fim(10, 120, null));
		AgentLimits pouco = new AgentLimits(6, 10, 100, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		List<AgentEvent> eventos = coleta(execucao(pouco, true));

		assertThat(chamadas).hasSize(1);
		assertThat(eventos).contains(new Status(Phase.GENERATING,
				"Limite de 100 tokens gerados atingido: a resposta foi interrompida."));
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.LENGTH, Limit.TOKENS, 1, 1));
	}

	@Test
	void respostaCortadaPeloOrcamentoDeTokensTerminaComLengthETokens() {
		responde(token("Um record é"), fimPorLimite(10, 100));
		AgentLimits pouco = new AgentLimits(6, 10, 100, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);

		List<AgentEvent> eventos = coleta(execucao(pouco, true));

		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.LENGTH, Limit.TOKENS, 1, 0));
	}

	@Test
	void respostaCortadaPelaJanelaDoModeloTerminaComLengthSemLimite() {
		responde(token("Um record é"), fimPorLimite(10, 20));

		List<AgentEvent> eventos = coleta(execucao(limites(), true));

		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.LENGTH, null, 1, 0));
	}

	@Test
	void estourarOTempoTotalFechaComOQueExisteEAvisa() {
		roteiro.add(() -> Flux.concat(Flux.just((ModelStreamEvent) token("Começo")), Flux.never()));
		AgentLimits curto = new AgentLimits(6, 10, 4096, Duration.ofMillis(300), Duration.ofSeconds(5), 8000);

		List<AgentEvent> eventos = coleta(execucao(curto, true));

		assertThat(eventos).contains(new AgentEvent.Token("Começo"));
		assertThat(eventos).anyMatch(e -> e instanceof Status s && s.message().contains("Limite de tempo de 300 ms"));
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.LENGTH, Limit.TIME, 1, 0));
		assertThat(eventos.get(eventos.size() - 2)).isInstanceOf(Usage.class);
	}

	// ---------------------------------------------------------------------------- cancelamento

	@Test
	void botaoPararFechaComCancelledEPassaOUsoAteAli() throws Exception {
		AtomicBoolean modeloCancelado = new AtomicBoolean();
		roteiro.add(() -> Flux.concat(Flux.just((ModelStreamEvent) token("Começo")), Flux.<ModelStreamEvent>never())
			.doOnCancel(() -> modeloCancelado.set(true)));
		LoopExecution execucao = execucao(limites(), true);
		List<AgentEvent> eventos = Collections.synchronizedList(new ArrayList<>());
		Disposable assinatura = execucao.events().subscribe(eventos::add);

		espera(() -> eventos.contains(new AgentEvent.Token("Começo")));
		execucao.cancel();
		espera(() -> eventos.stream().anyMatch(Finished.class::isInstance));

		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.CANCELLED, null, 1, 0));
		assertThat(eventos.get(eventos.size() - 2)).isInstanceOf(Usage.class);
		assertThat(modeloCancelado).as("a geração em andamento foi cancelada").isTrue();
		assertThat(assinatura.isDisposed()).isTrue();
	}

	@Test
	void pararAntesDeAssinarFazAExecucaoNascerCancelada() {
		responde(token("nunca"), fim(1, 1, null));
		LoopExecution execucao = execucao(limites(), true);

		execucao.cancel();
		List<AgentEvent> eventos = coleta(execucao);

		assertThat(eventos).noneMatch(AgentEvent.Token.class::isInstance);
		assertThat(eventos.getLast()).isEqualTo(new Finished(FinishReason.CANCELLED, null, 0, 0));
	}

	@Test
	void cancelarAAssinaturaInterrompeAFerramentaEmAndamento() throws Exception {
		responde(pede("lenta", "{\"ms\":30000}"), fim(1, 1, null));
		Disposable assinatura = execucao(limites(), true).events().subscribe();

		espera(ferramentas.lentaIniciou::get);
		assinatura.dispose();
		espera(ferramentas.lentaFoiInterrompida::get);

		assertThat(chamadas).as("não houve nova chamada ao modelo").hasSize(1);
	}

	// ----------------------------------------------------------------------------------- erros

	@Test
	void erroDoModeloSaiComoErroDoFluxo() {
		roteiro.add(() -> Flux.concat(Flux.just((ModelStreamEvent) token("Co")),
				Flux.<ModelStreamEvent>error(new ModelException(ErrorCode.OLLAMA_UNAVAILABLE, "caiu", true))));

		assertThatThrownBy(() -> coleta(execucao(limites(), true))).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE));
	}

	@Test
	void usoSomaPassosEPesaAVelocidadePelosTokens() {
		responde(pede("eco", "{\"texto\":\"a\"}"), fim(10, 100, 50.0));
		responde(token("ok"), fim(20, 100, 25.0));

		Usage uso = uso(coleta(execucao(limites(), true)));

		// 200 tokens em 100/50 + 100/25 = 6 s
		assertThat(uso.tokensPerSecond()).isCloseTo(200.0 / 6.0, within(0.01));
		assertThat(uso.totalDurationMs()).isGreaterThanOrEqualTo(0);
	}

	// -------------------------------------------------------------------------- auxiliares

	private LoopExecution execucao(AgentLimits limites, boolean comFerramentas) {
		return loop.start(entrada(ROTA, limites, comFerramentas, null));
	}

	private LoopInput entrada(Route rota, AgentLimits limites, boolean comFerramentas, String aviso) {
		return new LoopInput(rota, mensagens(), comFerramentas, aviso, null, limites, GenerationOptions.defaults(),
				Language.PT_BR);
	}

	private static List<Message> mensagens() {
		return List.of(new SystemMessage("Você é um agente."), new UserMessage("Pergunta?"));
	}

	private static AgentLimits limites() {
		return new AgentLimits(6, 10, 4096, Duration.ofMinutes(1), Duration.ofSeconds(5), 8000);
	}

	private void responde(ModelStreamEvent... eventos) {
		roteiro.add(() -> Flux.just(eventos));
	}

	private static ModelStreamEvent.Token token(String texto) {
		return new ModelStreamEvent.Token(texto);
	}

	/** O modelo pede ferramentas: pares nome, argumentos. */
	private static ToolCalls pede(String... nomeEArgumentos) {
		List<ToolCallRequest> pedidos = new ArrayList<>();
		for (int i = 0; i < nomeEArgumentos.length; i += 2) {
			pedidos.add(new ToolCallRequest(nomeEArgumentos[i], nomeEArgumentos[i + 1]));
		}
		return new ToolCalls(pedidos);
	}

	private static Completed fim(int prompt, int gerados, Double tokensPorSegundo) {
		return new Completed("m", ModelStreamEvent.FinishReason.STOP,
				new ModelStreamEvent.Usage(prompt, gerados, tokensPorSegundo, 5L, 0L, 10));
	}

	private static Completed fimPorLimite(int prompt, int gerados) {
		return new Completed("m", ModelStreamEvent.FinishReason.LENGTH,
				new ModelStreamEvent.Usage(prompt, gerados, null, 5L, 0L, 10));
	}

	private static List<AgentEvent> coleta(LoopExecution execucao) {
		return execucao.events().collectList().block(Duration.ofSeconds(15));
	}

	private static Usage uso(List<AgentEvent> eventos) {
		return (Usage) eventos.stream().filter(Usage.class::isInstance).findFirst().orElseThrow();
	}

	private static ToolResult resultadoDe(List<AgentEvent> eventos) {
		return (ToolResult) eventos.stream().filter(ToolResult.class::isInstance).findFirst().orElseThrow();
	}

	private static void espera(BooleanSupplier condicao) throws InterruptedException {
		long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!condicao.getAsBoolean()) {
			if (System.nanoTime() > limite) {
				throw new AssertionError("a condição não aconteceu em 10 s");
			}
			Thread.sleep(10);
		}
	}
}
