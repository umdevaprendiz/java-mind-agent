package com.example.java_mind_agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import com.example.java_mind_agent.agent.loop.AgentLoop;
import com.example.java_mind_agent.agent.loop.LoopExecution;
import com.example.java_mind_agent.agent.loop.LoopInput;
import com.example.java_mind_agent.agent.prompt.JavaVersionPromptContributor;
import com.example.java_mind_agent.agent.prompt.SystemPromptBuilder;
import com.example.java_mind_agent.agent.tool.ToolRegistry;
import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.knowledge.KnowledgeProperties;
import com.example.java_mind_agent.knowledge.version.JavaVersionDetector;
import com.example.java_mind_agent.knowledge.version.JavaVersionRegistry;
import com.example.java_mind_agent.knowledge.version.JavaVersionService;
import com.example.java_mind_agent.knowledge.version.VersionSource;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.routing.ModelRouter;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;
import com.example.java_mind_agent.settings.Settings;
import com.example.java_mind_agent.settings.SettingsService;

/**
 * Preparar uma execução: escolhe o modelo, decide a versão do Java, monta o prompt e as mensagens e liga ou não as
 * ferramentas. O loop é falso; o que ele recebeu é o que se confere.
 */
class AgentServiceTest {

	private static final AgentLimits LIMITES_PADRAO = new AgentLimits(6, 10, 4096, Duration.ofMinutes(15),
			Duration.ofSeconds(60), 8000);

	private final JavaVersionRegistry registry = new JavaVersionRegistry();

	private final JavaVersionService versoes = new JavaVersionService(registry, new JavaVersionDetector(registry),
			new KnowledgeProperties(null, 8));

	private final ModelRouter router = mock(ModelRouter.class);

	private final SettingsService preferencias = mock(SettingsService.class);

	private final AgentLoop loop = mock(AgentLoop.class);

	private final LoopExecution execucao = mock(LoopExecution.class);

	private ToolRegistry ferramentas = new ToolRegistry(List.of(new FerramentasDeTeste()));

	@BeforeEach
	void preparaMocks() {
		when(preferencias.get()).thenReturn(new Settings(Tier.MEDIUM, null, Language.PT_BR));
		when(router.resolve(any())).thenReturn(rota(true));
		when(loop.start(any())).thenReturn(execucao);
	}

	private AgentService servico() {
		return new AgentService(router, preferencias, versoes,
				new SystemPromptBuilder(List.of(new JavaVersionPromptContributor())), ferramentas, loop,
				new AgentProperties(LIMITES_PADRAO, new AgentProperties.Debug(false)));
	}

	private static Route rota(Boolean suportaFerramentas) {
		return new Route(Tier.MEDIUM, "Médio", "qwen2.5:7b", "http://localhost:11434", false, true, true,
				suportaFerramentas);
	}

	private LoopInput prepara(AgentRequest pedido) {
		servico().prepare(pedido);
		ArgumentCaptor<LoopInput> capturado = ArgumentCaptor.forClass(LoopInput.class);
		verify(loop, atLeastOnce()).start(capturado.capture());
		return capturado.getValue();
	}

	// -------------------------------------------------------------------------------- modelo

	@Test
	void semNivelPedidoOServicoDeixaOPadraoParaORouter() {
		servico().prepare(AgentRequest.of("O que é um record?"));

		verify(router).resolve(null);
	}

	@Test
	void usaONivelPedido() {
		Route forte = new Route(Tier.STRONG, "Forte", "qwen2.5:14b", "http://localhost:11434", false, false, false, true);
		when(router.resolve(Tier.STRONG)).thenReturn(forte);

		AgentRun run = servico().prepare(
				new AgentRequest("Pergunta", List.of(), Tier.STRONG, null, null, null, null, null));

		assertThat(run.route()).isEqualTo(forte);
	}

	@Test
	void falhaDeModeloAcontecePrepararENaoNoStream() {
		when(router.resolve(any())).thenThrow(new ModelException(ErrorCode.MODEL_NOT_INSTALLED, "faltando", false));

		assertThatThrownBy(() -> servico().prepare(AgentRequest.of("x"))).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED));
	}

	// ------------------------------------------------------------------------ mensagens e prompt

	@Test
	void montaSistemaHistoricoEPerguntaNessaOrdem() {
		List<Message> historico = List.of(new UserMessage("oi"), new AssistantMessage("olá"));

		LoopInput entrada = prepara(
				new AgentRequest("E um enum?", historico, null, null, null, null, null, null));

		assertThat(entrada.messages()).hasSize(4);
		assertThat(entrada.messages().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(entrada.messages().get(1)).isEqualTo(historico.get(0));
		assertThat(entrada.messages().get(2)).isEqualTo(historico.get(1));
		assertThat(entrada.messages().get(3)).isEqualTo(new UserMessage("E um enum?"));
	}

	@Test
	void oPromptDeSistemaTrazOTextoBaseEAVersaoDoJavaAssumida() {
		LoopInput entrada = prepara(AgentRequest.of("No Java 11, como uso var?"));

		String sistema = entrada.messages().getFirst().getText();
		assertThat(sistema).startsWith("Você é o Java Mind Agent")
			.contains("Versão do Java assumida: Java 11 (citada na pergunta).");
	}

	@Test
	void aRespostaAbreComADeclaracaoDaVersaoEscritaPeloAgenteNoIdiomaDaResposta() {
		LoopInput pt = prepara(AgentRequest.of("No Java 11, como uso var?"));
		LoopInput en = prepara(
				new AgentRequest("In Java 11, how do I use var?", List.of(), null, null, Language.EN, null, null, null));

		assertThat(pt.preface()).isEqualTo("Versão do Java assumida: Java 11 (citada na pergunta).\n\n");
		assertThat(en.preface()).isEqualTo("Java version assumed: Java 11 (mentioned in the question).\n\n");
	}

	@Test
	void aVersaoDoJavaSegueAPoliticaDaF10ComAPreferenciaSalva() {
		when(preferencias.get()).thenReturn(new Settings(Tier.MEDIUM, 17, Language.PT_BR));

		AgentRun semPista = servico().prepare(AgentRequest.of("Qual a diferença entre ArrayList e LinkedList?"));
		AgentRun escolhida = servico().prepare(
				new AgentRequest("Qual a diferença entre ArrayList e LinkedList?", List.of(), null, 21, null, null, null, null));

		assertThat(semPista.javaVersion().version()).isEqualTo(17);
		assertThat(semPista.javaVersion().source()).isEqualTo(VersionSource.SETTINGS);
		assertThat(escolhida.javaVersion().version()).isEqualTo(21);
		assertThat(escolhida.javaVersion().source()).isEqualTo(VersionSource.SELECTED);
	}

	@Test
	void oIdiomaDoPedidoVenceAPreferencia() {
		LoopInput entrada = prepara(
				new AgentRequest("What is a record?", List.of(), null, null, Language.EN, null, null, null));

		assertThat(entrada.language()).isEqualTo(Language.EN);
		assertThat(entrada.messages().getFirst().getText()).startsWith("You are Java Mind Agent")
			.contains("Java version assumed:");
	}

	@Test
	void semIdiomaNoPedidoValeOIdiomaSalvo() {
		when(preferencias.get()).thenReturn(new Settings(Tier.MEDIUM, null, Language.EN));

		LoopInput entrada = prepara(AgentRequest.of("x"));

		assertThat(entrada.language()).isEqualTo(Language.EN);
	}

	// -------------------------------------------------------------------------- ferramentas

	@Test
	void modeloQueSuportaFerramentasRecebeAsFerramentas() {
		LoopInput entrada = prepara(AgentRequest.of("x"));

		assertThat(entrada.toolsEnabled()).isTrue();
		assertThat(entrada.notice()).isNull();
	}

	@Test
	void modeloSemSuporteOuSemInformacaoRespondeSemFerramentasEAvisa() {
		for (Boolean suporte : new Boolean[] { false, null }) {
			when(router.resolve(any())).thenReturn(rota(suporte));

			AgentRun run = servico().prepare(AgentRequest.of("x"));

			assertThat(run.toolsEnabled()).as("suporte = %s", suporte).isFalse();
		}
		ArgumentCaptor<LoopInput> capturado = ArgumentCaptor.forClass(LoopInput.class);
		verify(loop, times(2)).start(capturado.capture());
		assertThat(capturado.getAllValues()).allSatisfy(entrada -> {
			assertThat(entrada.toolsEnabled()).isFalse();
			assertThat(entrada.notice())
				.isEqualTo("O modelo qwen2.5:7b não confirmou suporte a ferramentas: respondo sem usá-las.");
		});
	}

	@Test
	void semNenhumaFerramentaRegistradaNaoHaAvisoNemFerramentas() {
		ferramentas = new ToolRegistry(List.of());
		when(router.resolve(any())).thenReturn(rota(true), rota(false));

		AgentRun comSuporte = servico().prepare(AgentRequest.of("x"));
		AgentRun semSuporte = servico().prepare(AgentRequest.of("x"));

		assertThat(comSuporte.toolsEnabled()).isFalse();
		assertThat(semSuporte.toolsEnabled()).isFalse();
		ArgumentCaptor<LoopInput> capturado = ArgumentCaptor.forClass(LoopInput.class);
		verify(loop, times(2)).start(capturado.capture());
		assertThat(capturado.getAllValues()).allSatisfy(e -> assertThat(e.notice()).isNull());
	}

	// ----------------------------------------------------------------------------- limites

	@Test
	void usaOsLimitesDaConfiguracaoOuOsDoPedido() {
		AgentLimits dois = new AgentLimits(2, 1, 100, Duration.ofSeconds(30), Duration.ofSeconds(5), 500);
		GenerationOptions criativo = new GenerationOptions(1.0, null, null, null, null, null, null, null);

		LoopInput padrao = prepara(AgentRequest.of("x"));
		assertThat(padrao.limits()).isEqualTo(LIMITES_PADRAO);
		assertThat(padrao.generation()).isEqualTo(GenerationOptions.defaults());

		servico().prepare(new AgentRequest("x", List.of(), null, null, null, null, dois, criativo));
		ArgumentCaptor<LoopInput> capturado = ArgumentCaptor.forClass(LoopInput.class);
		verify(loop, times(2)).start(capturado.capture());
		assertThat(capturado.getAllValues().get(1).limits()).isEqualTo(dois);
		assertThat(capturado.getAllValues().get(1).generation()).isEqualTo(criativo);
	}

	@Test
	void aExecucaoPreparadaDelegaEventosECancelamentoAoLoop() {
		AgentRun run = servico().prepare(AgentRequest.of("x"));

		run.events();
		run.cancel();

		verify(execucao).events();
		verify(execucao).cancel();
	}

	@Test
	void pedidoSemPerguntaEhRecusado() {
		assertThatThrownBy(() -> AgentRequest.of("  ")).isInstanceOf(IllegalArgumentException.class);
	}
}
