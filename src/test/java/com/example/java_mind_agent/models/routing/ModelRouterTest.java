package com.example.java_mind_agent.models.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.common.persistence.AppSetting;
import com.example.java_mind_agent.common.persistence.AppSettingRepository;
import com.example.java_mind_agent.models.catalog.ModelCatalogService;
import com.example.java_mind_agent.models.hardware.HardwareInfo;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ModelsProperties;
import com.example.java_mind_agent.models.ModelsProperties.TierProperties;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent.Token;
import com.example.java_mind_agent.models.ollama.OllamaChatService;
import com.example.java_mind_agent.models.ollama.OllamaHosts;
import com.example.java_mind_agent.models.Speed;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.support.ModelsTestData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * O roteador escolhe o modelo certo para cada requisição e, quando o nível pedido não pode responder, diz o que fazer
 * em vez de trocar de nível por conta própria. Dois Ollamas falsos (o padrão e um "remoto") e a máquina de
 * desenvolvimento simulada (15,76 GiB: Baixo, Médio e Forte cabem; o Extra, não). O chat em si é simulado: o que
 * importa aqui é para onde o roteador o manda.
 */
class ModelRouterTest {

	private static final HardwareInfo DESENVOLVIMENTO = new HardwareInfo(15.76, 2.5, "Intel(R) UHD Graphics", true);

	private static final List<Message> HISTORICO = List.of(new UserMessage("O que é um record?"),
			new AssistantMessage("Uma classe de dados imutável."), new UserMessage("E um sealed interface?"));

	private static WireMockServer padrao;

	private static WireMockServer remoto;

	private final List<ModelCatalogService> catalogos = new ArrayList<>();

	private OllamaChatService chat;

	@BeforeAll
	static void sobemOsOllamasFalsos() {
		padrao = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		padrao.start();
		remoto = new WireMockServer(WireMockConfiguration.options().dynamicPort());
		remoto.start();
	}

	@AfterAll
	static void desligamOsOllamasFalsos() {
		padrao.stop();
		remoto.stop();
	}

	@BeforeEach
	void preparaOCenarioPadrao() {
		padrao.resetAll();
		remoto.resetAll();
		// Instalados: Baixo (3B) e Médio (7B). O Forte não foi baixado; o Extra não cabe nesta máquina.
		instalados(padrao, "qwen2.5:3b", "qwen2.5:7b");
		naMemoria(padrao);
		padrao.stubFor(post("/api/show").willReturn(json("{\"capabilities\":[\"completion\",\"tools\"]}")));
		chat = mock(OllamaChatService.class);
		when(chat.stream(anyString(), anyString(), anyList(), any(), anyList())).thenReturn(Flux.empty());
	}

	@AfterEach
	void encerra() {
		catalogos.forEach(ModelCatalogService::shutdown);
	}

	// ================================================================ escolher o nível

	@Test
	void usaOTierPedidoComOModeloEOOllamaDele() {
		Route rota = roteador().resolve(Tier.LOW);

		assertThat(rota).isEqualTo(new Route(Tier.LOW, "Baixo", "qwen2.5:3b", padrao.baseUrl(), false, false, false,
				true));
	}

	@Test
	void semTierValeOPadraoDaConfiguracaoEIssoFicaMarcado() {
		Route rota = roteador().resolve(null);

		assertThat(rota.tier()).isEqualTo(Tier.MEDIUM);
		assertThat(rota.model()).isEqualTo("qwen2.5:7b");
		assertThat(rota.fromDefault()).isTrue();
	}

	@Test
	void oPadraoSalvoPeloUsuarioVenceAConfiguracao() {
		Route rota = roteador(ModelsTestData.tiers(), Tier.LOW).resolve(null);

		assertThat(rota.tier()).isEqualTo(Tier.LOW);
		assertThat(rota.fromDefault()).isTrue();
	}

	@Test
	void tierPedidoValeMesmoQuandoHaUmPadraoDiferente() {
		Route rota = roteador(ModelsTestData.tiers(), Tier.LOW).resolve(Tier.MEDIUM);

		assertThat(rota.tier()).isEqualTo(Tier.MEDIUM);
		assertThat(rota.fromDefault()).isFalse();
	}

	@Test
	void avisaQuandoOModeloJaEstaNaMemoriaParaAInterfaceNaoMostrarCarregando() {
		naMemoria(padrao, "qwen2.5:7b");

		assertThat(roteador().resolve(Tier.MEDIUM).loaded()).isTrue();
		assertThat(roteador().resolve(Tier.LOW).loaded()).isFalse();
	}

	@Test
	void consultaSoOOllamaDoTierEscolhido() {
		instalados(remoto, "qwen3:32b");
		naMemoria(remoto);
		remoto.stubFor(post("/api/show").willReturn(json("{\"capabilities\":[\"completion\"]}")));
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, remoto.baseUrl()));
		ModelRouter roteador = roteador(tiers, null);

		roteador.resolve(Tier.LOW);
		assertThat(remoto.getAllServeEvents()).as("o Ollama remoto nem foi consultado").isEmpty();

		roteador.resolve(Tier.EXTRA);
		assertThat(padrao.getAllServeEvents().stream().filter(e -> e.getRequest().getUrl().startsWith("/api/ps")))
			.as("o Ollama padrão só foi consultado pelo Baixo")
			.hasSize(1);
	}

	@Test
	void tierEmOllamaRemotoVaiParaOHostDele() {
		instalados(remoto, "qwen3:32b");
		naMemoria(remoto);
		remoto.stubFor(post("/api/show").willReturn(json("{\"capabilities\":[\"completion\"]}")));
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, remoto.baseUrl()));

		Route rota = roteador(tiers, null).resolve(Tier.EXTRA);

		assertThat(rota).isEqualTo(new Route(Tier.EXTRA, "Extra", "qwen3:32b", remoto.baseUrl(), true, false, false,
				false));
	}

	// ====================================== nunca trocar de nível em silêncio

	@Test
	void modeloNaoInstaladoEErroAcionavelComOMenorNivelSugerido() {
		assertThatThrownBy(() -> roteador().resolve(Tier.STRONG)).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED);
			assertThat(e.isRetryable()).isFalse();
			assertThat(e.getMessage()).contains("qwen2.5:14b").contains("Baixe-o antes").contains("Médio")
				.contains("qwen2.5:7b")
				.contains("está instalado e cabe");
			assertThat(e.getDetails()).containsEntry("tier", "strong").containsEntry("suggestedTier", "medium");
		});
	}

	@Test
	void naoTrocaDeNivelSozinhoNemTentaOMenorQuandoOEscolhidoFalha() {
		ModelRouter roteador = roteador();

		assertThatThrownBy(() -> roteador.resolve(Tier.STRONG)).isInstanceOf(ModelException.class);

		verifyNoInteractions(chat);
	}

	@Test
	void semTierEComPadraoQueNaoPodeResponderFalhaEmVezDeUsarOutro() {
		ModelRouter roteador = roteador(ModelsTestData.tiers(), Tier.STRONG);

		assertThatThrownBy(() -> roteador.resolve(null)).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED);
			assertThat(e.getDetails()).as("diz qual nível era o padrão que falhou").containsEntry("tier", "strong");
		});
		verifyNoInteractions(chat);
	}

	@Test
	void naoCabeNestaMaquinaSugereOMaiorNivelMenorQueJaDaParaUsar() {
		assertThatThrownBy(() -> roteador().resolve(Tier.EXTRA)).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT);
			assertThat(e.getMessage()).contains("qwen3:32b").contains("26,0 GB").contains("12,8 GB").contains("Médio");
			// O Forte cabe mas não está instalado: o Médio, que já responde agora, é a sugestão.
			assertThat(e.getDetails()).containsEntry("tier", "extra").containsEntry("suggestedTier", "medium");
		});
	}

	@Test
	void preferenciaPeloInstaladoMesmoQueUmMaiorPrecisasseSerBaixado() {
		instalados(padrao, "qwen2.5:3b");

		assertThatThrownBy(() -> roteador().resolve(Tier.STRONG)).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getDetails()).containsEntry("suggestedTier", "low"));
	}

	@Test
	void seNadaMenorEstaInstaladoSugereOQueCabeEPrecisaSerBaixado() {
		instalados(padrao);

		assertThatThrownBy(() -> roteador().resolve(Tier.STRONG)).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getMessage()).contains("Médio").contains("precisa ser baixado antes");
			assertThat(e.getDetails()).containsEntry("suggestedTier", "medium");
		});
	}

	@Test
	void semNenhumNivelMenorNaoHaSugestaoMasHaOTierQueFalhou() {
		instalados(padrao);

		assertThatThrownBy(() -> roteador().resolve(Tier.LOW)).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED);
			assertThat(e.getDetails()).containsEntry("tier", "low").doesNotContainKey("suggestedTier");
		});
	}

	@Test
	void nuncaSugereUmNivelMaiorQueOPedido() {
		instalados(padrao, "qwen2.5:7b");

		assertThatThrownBy(() -> roteador().resolve(Tier.LOW)).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getDetails()).doesNotContainKey("suggestedTier"));
	}

	@Test
	void ollamaDoTierForaDoArEErroRetomavelComSugestaoDeUmNivelQueFunciona() throws IOException {
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW,
				"http://localhost:" + portaFechada()));

		assertThatThrownBy(() -> roteador(tiers, null).resolve(Tier.EXTRA)).isInstanceOfSatisfying(ModelException.class,
				e -> {
					assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
					assertThat(e.isRetryable()).isTrue();
					assertThat(e.getDetails()).containsEntry("tier", "extra").containsEntry("suggestedTier", "medium");
				});
	}

	@Test
	void ollamaPadraoForaDoArNaoTemSugestaoPoisNenhumNivelResponde() throws IOException {
		ModelRouter semOllama = roteador(ModelsTestData.tiers(), null, "http://localhost:" + portaFechada());

		assertThatThrownBy(() -> semOllama.resolve(Tier.MEDIUM)).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).isTrue();
			assertThat(e.getDetails()).containsEntry("tier", "medium").doesNotContainKey("suggestedTier");
		});
	}

	// ============================================================ gerar a resposta

	@Test
	void geraNoModeloEOllamaDaRotaComOMesmoHistoricoSemTocarNele() {
		ModelRouter roteador = roteador();
		Route rota = roteador.resolve(Tier.MEDIUM);
		List<Message> copia = new ArrayList<>(HISTORICO);
		GenerationOptions opcoes = GenerationOptions.defaults();

		roteador.stream(rota, HISTORICO, opcoes).blockLast();

		verify(chat).stream(eq(padrao.baseUrl()), eq("qwen2.5:7b"), same(HISTORICO), same(opcoes), eq(List.of()));
		assertThat(HISTORICO).isEqualTo(copia);
	}

	@Test
	void asFerramentasDoAgenteChegamAoClienteDoOllamaSemMudar() {
		ModelRouter roteador = roteador();
		Route rota = roteador.resolve(Tier.MEDIUM);
		GenerationOptions opcoes = GenerationOptions.defaults();
		List<org.springframework.ai.tool.ToolCallback> ferramentas = List.of(
				mock(org.springframework.ai.tool.ToolCallback.class));

		roteador.stream(rota, HISTORICO, opcoes, ferramentas).blockLast();

		verify(chat).stream(eq(padrao.baseUrl()), eq("qwen2.5:7b"), same(HISTORICO), same(opcoes), same(ferramentas));
	}

	@Test
	void trocarDeNivelNoMeioDaConversaUsaOutroModeloComOMesmoHistorico() {
		ModelRouter roteador = roteador();
		GenerationOptions opcoes = GenerationOptions.defaults();

		roteador.stream(roteador.resolve(Tier.LOW), HISTORICO, opcoes).blockLast();
		roteador.stream(roteador.resolve(Tier.MEDIUM), HISTORICO, opcoes).blockLast();

		verify(chat).stream(eq(padrao.baseUrl()), eq("qwen2.5:3b"), same(HISTORICO), same(opcoes), eq(List.of()));
		verify(chat).stream(eq(padrao.baseUrl()), eq("qwen2.5:7b"), same(HISTORICO), same(opcoes), eq(List.of()));
		assertThat(HISTORICO).hasSize(3);
	}

	@Test
	void respostaDoModeloPassaSemAlteracao() {
		when(chat.stream(anyString(), anyString(), anyList(), any(), anyList())).thenReturn(Flux.just(new Token("Um "),
				new Token("record")));
		ModelRouter roteador = roteador();

		StepVerifier.create(roteador.stream(roteador.resolve(Tier.LOW), HISTORICO, GenerationOptions.defaults()))
			.expectNext(new Token("Um "), new Token("record"))
			.verifyComplete();
	}

	@Test
	void faltaDeMemoriaNaHoraDeGerarTambemSugereONivelMenor() {
		when(chat.stream(anyString(), anyString(), anyList(), any(), anyList())).thenReturn(Flux.concat(Flux.just(new Token("Um")),
				Flux.error(new ModelException(ErrorCode.MODEL_DOES_NOT_FIT,
						"O modelo 'qwen2.5:7b' não cabe na memória disponível (precisa de 9.1 GiB, há 2.0 GiB).",
						false))));
		ModelRouter roteador = roteador();
		Route medio = roteador.resolve(Tier.MEDIUM);

		StepVerifier.create(roteador.stream(medio, HISTORICO, GenerationOptions.defaults()))
			.expectNext(new Token("Um"))
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class, e -> {
				assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT);
				assertThat(e.getMessage()).contains("precisa de 9.1 GiB").contains("Baixo").contains("qwen2.5:3b");
				assertThat(e.getDetails()).containsEntry("tier", "medium").containsEntry("suggestedTier", "low");
			}))
			.verify(Duration.ofSeconds(10));
		verify(chat, times(1)).stream(anyString(), anyString(), anyList(), any(), anyList());
	}

	@Test
	void modeloRemovidoDepoisDeResolverTambemViraErroAcionavel() {
		when(chat.stream(anyString(), anyString(), anyList(), any(), anyList())).thenReturn(Flux.error(new ModelException(
				ErrorCode.MODEL_NOT_INSTALLED, "O modelo 'qwen2.5:7b' não está instalado.", false)));
		ModelRouter roteador = roteador();
		Route medio = roteador.resolve(Tier.MEDIUM);

		StepVerifier.create(roteador.stream(medio, HISTORICO, GenerationOptions.defaults()))
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class,
					e -> assertThat(e.getDetails()).containsEntry("tier", "medium")))
			.verify(Duration.ofSeconds(10));
	}

	@Test
	void outrosErrosPassamIntactosESemTentarOutroNivel() {
		ModelException original = new ModelException(ErrorCode.GENERATION_TIMEOUT, "Demorou demais.", true);
		when(chat.stream(anyString(), anyString(), anyList(), any(), anyList())).thenReturn(Flux.error(original));
		ModelRouter roteador = roteador();
		Route medio = roteador.resolve(Tier.MEDIUM);

		StepVerifier.create(roteador.stream(medio, HISTORICO, GenerationOptions.defaults()))
			.expectErrorMatches(erro -> erro == original)
			.verify(Duration.ofSeconds(10));
		verify(chat, times(1)).stream(anyString(), anyString(), anyList(), any(), anyList());
		verify(chat, never()).stream(anyString(), eq("qwen2.5:3b"), anyList(), any(), anyList());
		assertThat(original.getDetails()).isEmpty();
	}

	@Test
	void rotaRemotaGeraNoHostRemoto() {
		instalados(remoto, "qwen3:32b");
		naMemoria(remoto);
		remoto.stubFor(post("/api/show").willReturn(json("{\"capabilities\":[\"completion\"]}")));
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, remoto.baseUrl()));
		ModelRouter roteador = roteador(tiers, null);

		roteador.stream(roteador.resolve(Tier.EXTRA), HISTORICO, GenerationOptions.defaults()).blockLast();

		verify(chat).stream(eq(remoto.baseUrl()), eq("qwen3:32b"), same(HISTORICO), any(), eq(List.of()));
	}

	// ================================================================== auxiliares

	private ModelRouter roteador() {
		return roteador(ModelsTestData.tiers(), null);
	}

	private ModelRouter roteador(Map<Tier, TierProperties> tiers, Tier padraoSalvo) {
		return roteador(tiers, padraoSalvo, padrao.baseUrl());
	}

	private ModelRouter roteador(Map<Tier, TierProperties> tiers, Tier padraoSalvo, String ollamaPadrao) {
		AppSettingRepository configuracoes = mock(AppSettingRepository.class);
		when(configuracoes.findById(anyString())).thenReturn(Optional.empty());
		if (padraoSalvo != null) {
			when(configuracoes.findById(ModelCatalogService.DEFAULT_TIER_SETTING))
				.thenReturn(Optional.of(new AppSetting(ModelCatalogService.DEFAULT_TIER_SETTING, padraoSalvo.value())));
		}
		ModelsProperties propriedades = ModelsTestData.properties(Tier.MEDIUM, tiers, ModelsTestData.embedding(null));
		ModelCatalogService catalogo = new ModelCatalogService(propriedades, new OllamaHosts(ollamaPadrao),
				() -> DESENVOLVIMENTO, configuracoes);
		catalogos.add(catalogo);
		return new ModelRouter(catalogo, chat);
	}

	private static int portaFechada() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String corpo) {
		return aResponse().withHeader("Content-Type", "application/json").withBody(corpo);
	}

	private static void instalados(WireMockServer servidor, String... modelos) {
		List<String> itens = new ArrayList<>();
		for (String modelo : modelos) {
			itens.add("{\"name\":\"%s\",\"model\":\"%s\",\"size\":1900000000,\"digest\":\"d-%s\"}".formatted(modelo,
					modelo, modelo));
		}
		servidor.stubFor(get("/api/tags").willReturn(json("{\"models\":[" + String.join(",", itens) + "]}")));
	}

	private static void naMemoria(WireMockServer servidor, String... modelos) {
		List<String> itens = new ArrayList<>();
		for (String modelo : modelos) {
			itens.add("{\"name\":\"%s\",\"size\":6000000000,\"size_vram\":0}".formatted(modelo));
		}
		servidor.stubFor(get("/api/ps").willReturn(json("{\"models\":[" + String.join(",", itens) + "]}")));
	}
}
