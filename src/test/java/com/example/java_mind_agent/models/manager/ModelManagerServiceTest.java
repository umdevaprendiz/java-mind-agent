package com.example.java_mind_agent.models.manager;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.common.persistence.AppSettingRepository;
import com.example.java_mind_agent.models.catalog.ModelCatalogService;
import com.example.java_mind_agent.models.hardware.HardwareDetector;
import com.example.java_mind_agent.models.hardware.HardwareInfo;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ModelsProperties;
import com.example.java_mind_agent.models.ModelsProperties.TierProperties;
import com.example.java_mind_agent.models.ollama.OllamaHosts;
import com.example.java_mind_agent.models.Speed;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.support.ModelsTestData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Baixar, carregar, descarregar e remover modelos, contra dois Ollamas falsos (o padrão e um "remoto") e com a máquina
 * de desenvolvimento simulada (15,76 GiB: Baixo, Médio e Forte cabem; o Extra, não).
 */
class ModelManagerServiceTest {

	private static final HardwareInfo DESENVOLVIMENTO = new HardwareInfo(15.76, 2.5, "Intel(R) UHD Graphics", true);

	private static final String SUCCESS = "{\"status\":\"success\"}";

	private static WireMockServer padrao;

	private static WireMockServer remoto;

	private final List<ModelManagerService> gerentes = new ArrayList<>();

	private final List<ModelCatalogService> catalogos = new ArrayList<>();

	private final List<Disposable> assinaturas = new ArrayList<>();

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
		// Instalados: Baixo (3B), Médio (7B) e o de embedding. O Forte não foi baixado; o Extra não cabe.
		tags(padrao, tag("qwen2.5:3b", 1_900_000_000L), tag("qwen2.5:7b", 4_683_087_332L), tag("bge-m3:latest", 1_157_672_605L));
		emMemoria(padrao);
		padrao.stubFor(post("/api/show").willReturn(json("{\"capabilities\":[\"completion\",\"tools\"]}")));
	}

	@AfterEach
	void encerra() {
		assinaturas.forEach(Disposable::dispose);
		gerentes.forEach(ModelManagerService::shutdown);
		catalogos.forEach(ModelCatalogService::shutdown);
	}

	// ================================================================== baixar

	@Test
	void baixaEDevolveOProgressoSomadoDeTodasAsCamadasAteODone() {
		pull(padrao, "{\"status\":\"pulling manifest\"}", camada("aaaaaaaaaaaa", 800, 0), camada("aaaaaaaaaaaa", 800, 400),
				camada("bbbbbbbbbbbb", 200, 0), "{\"status\":\"verifying sha256 digest\"}",
				"{\"status\":\"writing manifest\"}", SUCCESS);

		List<PullEvent> eventos = gerente().pull("strong").collectList().block();

		assertThat(eventos).containsExactly(new PullEvent.Progress("pulling manifest", null, null, null),
				new PullEvent.Progress("downloading", 0L, 800L, 0.0),
				new PullEvent.Progress("downloading", 400L, 800L, 50.0),
				new PullEvent.Progress("downloading", 400L, 1000L, 40.0),
				new PullEvent.Progress("verifying sha256 digest", 400L, 1000L, 40.0),
				new PullEvent.Progress("writing manifest", 400L, 1000L, 40.0), new PullEvent.Done());
		padrao.verify(postRequestedFor(urlEqualTo("/api/pull"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:14b\",\"stream\":true}")));
	}

	@Test
	void baixarOModeloDeEmbeddingPedeOBgeM3() {
		pull(padrao, SUCCESS);

		assertThat(gerente().pull("embedding").collectList().block()).containsExactly(new PullEvent.Done());

		padrao.verify(postRequestedFor(urlEqualTo("/api/pull"))
			.withRequestBody(equalToJson("{\"model\":\"bge-m3\",\"stream\":true}")));
	}

	@Test
	void modeloJaInstaladoPodeSerBaixadoDeNovoParaAtualizar() {
		pull(padrao, SUCCESS);

		assertThat(gerente().pull("low").collectList().block()).containsExactly(new PullEvent.Done());
	}

	@Test
	void idQueNaoExisteEhNotFoundSemFalarComOOllama() {
		assertThatThrownBy(() -> gerente().pull("huge")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
			assertThat(e.getMessage()).contains("'huge'").contains("low, medium, strong, extra ou embedding");
		});
		assertThatThrownBy(() -> gerente().pull("LOW")).isInstanceOf(ModelException.class);
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/pull")));
	}

	@Test
	void ollamaForaDoArEhErroImediatoAntesDeQualquerStream() throws IOException {
		ModelManagerService semOllama = gerente(DESENVOLVIMENTO, "http://localhost:" + portaFechada(), ModelsTestData.tiers());

		assertThatThrownBy(() -> semOllama.pull("strong")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).isTrue();
		});
	}

	@Test
	void tierQueNaoCabeNestaMaquinaNaoEBaixadoEExplicaOMotivo() {
		assertThatThrownBy(() -> gerente().pull("extra")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT);
			assertThat(e.getMessage()).contains("qwen3:32b").contains("26,0 GB").contains("12,8 GB").contains("base-url");
		});
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/pull")));
	}

	@Test
	void tierEmHostRemotoEBaixadoNoHostRemotoEPresumidamenteCabe() {
		tags(remoto);
		pull(remoto, SUCCESS);
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, remoto.baseUrl()));

		List<PullEvent> eventos = gerente(DESENVOLVIMENTO, padrao.baseUrl(), tiers).pull("extra").collectList().block();

		assertThat(eventos).containsExactly(new PullEvent.Done());
		remoto.verify(postRequestedFor(urlEqualTo("/api/pull"))
			.withRequestBody(equalToJson("{\"model\":\"qwen3:32b\",\"stream\":true}")));
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/pull")));
	}

	@Test
	void segundoDownloadDoMesmoModeloEhConflitoEOCancelamentoLiberaOModelo() {
		pullLento(padrao);
		ModelManagerService gerente = gerente();
		assinaturas.add(gerente.pull("strong").subscribe(evento -> { }, erro -> { }));

		assertThatThrownBy(() -> gerente.pull("strong")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.CONFLICT);
			assertThat(e.isRetryable()).isFalse();
			assertThat(e.getMessage()).contains("qwen2.5:14b").contains("já está sendo baixado");
		});
		assinaturas.forEach(Disposable::dispose);

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(gerente.pull("strong")).isNotNull());
	}

	@Test
	void doisPedidosCriadosJuntosSoUmComecaOutroFalhaNoStreamComConflito() {
		pullLento(padrao);
		ModelManagerService gerente = gerente();
		Flux<PullEvent> primeiro = gerente.pull("strong");
		Flux<PullEvent> segundo = gerente.pull("strong");
		assinaturas.add(primeiro.subscribe(evento -> { }, erro -> { }));

		StepVerifier.create(segundo)
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class,
					e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CONFLICT)))
			.verify(Duration.ofSeconds(5));
		// O primeiro assina em outra thread: espera o pedido dele chegar e confere que o segundo não mandou outro.
		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> padrao.verify(1, postRequestedFor(urlEqualTo("/api/pull"))));
		await().during(Duration.ofMillis(300))
			.atMost(Duration.ofSeconds(2))
			.untilAsserted(() -> padrao.verify(1, postRequestedFor(urlEqualTo("/api/pull"))));
	}

	@Test
	void umFluxQueNinguemAssinouNaoPrendeOModelo() {
		ModelManagerService gerente = gerente();
		gerente.pull("strong");

		pull(padrao, SUCCESS);
		assertThat(gerente.pull("strong").collectList().block()).containsExactly(new PullEvent.Done());
	}

	@Test
	void modelosDeOutrosTiersBaixamAoMesmoTempo() {
		pullLento(padrao);
		ModelManagerService gerente = gerente();
		assinaturas.add(gerente.pull("strong").subscribe(evento -> { }, erro -> { }));

		assertThat(gerente.pull("embedding")).isNotNull();
	}

	@Test
	void erroNoMeioDoDownloadChegaDepoisDoProgressoEMapeadoELiberaOModelo() {
		pull(padrao, "{\"status\":\"pulling manifest\"}", camada("aaaaaaaaaaaa", 1000, 100),
				"{\"error\":\"write blob: no space left on device\"}");
		ModelManagerService gerente = gerente();

		StepVerifier.create(gerente.pull("strong"))
			.expectNextCount(2)
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class, e -> {
				assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
				assertThat(e.getMessage()).contains("Sem espaço em disco");
			}))
			.verify(Duration.ofSeconds(5));

		pull(padrao, SUCCESS);
		assertThat(gerente.pull("strong").collectList().block()).containsExactly(new PullEvent.Done());
	}

	@Test
	void nomeQueNaoExisteNoRegistroEhNotFoundDentroDoStream() {
		pull(padrao, "{\"status\":\"pulling manifest\"}", "{\"error\":\"pull model manifest: file does not exist\"}");

		StepVerifier.create(gerente().pull("strong"))
			.expectNextCount(1)
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class,
					e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND)))
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void streamQueAcabaSemSuccessEErroRetomavel() {
		pull(padrao, "{\"status\":\"pulling manifest\"}", camada("aaaaaaaaaaaa", 1000, 400));

		StepVerifier.create(gerente().pull("strong"))
			.expectNextCount(2)
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class, e -> {
				assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
				assertThat(e.isRetryable()).isTrue();
			}))
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void erroHttpDoOllamaNoDownloadViraErroDoStream() {
		padrao.stubFor(post("/api/pull").willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

		StepVerifier.create(gerente().pull("strong"))
			.expectErrorSatisfies(erro -> assertThat(erro).isInstanceOfSatisfying(ModelException.class, e -> {
				assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
				assertThat(e.getMessage()).contains("boom");
			}))
			.verify(Duration.ofSeconds(5));
	}

	// ============================================================ carregar na memória

	@Test
	void carregaEmSegundoPlanoEPedirDeNovoEnquantoCarregaNaoCarregaDuasVezes() {
		padrao.stubFor(post("/api/generate").willReturn(json("{\"done\":true}").withFixedDelay(1000)));
		ModelManagerService gerente = gerente();

		gerente.warmUp("medium");
		gerente.warmUp("medium");

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> padrao.verify(1, postRequestedFor(urlEqualTo("/api/generate"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:7b\",\"keep_alive\":\"10m\",\"stream\":false}"))));
		await().during(Duration.ofMillis(1500))
			.atMost(Duration.ofSeconds(4))
			.untilAsserted(() -> padrao.verify(1, postRequestedFor(urlEqualTo("/api/generate"))));
	}

	@Test
	void modeloDeEmbeddingEhCarregadoPeloEmbed() {
		padrao.stubFor(post("/api/embed").willReturn(json("{\"embeddings\":[]}")));

		gerente().warmUp("embedding");

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> padrao.verify(1, postRequestedFor(urlEqualTo("/api/embed"))
			.withRequestBody(equalToJson("{\"model\":\"bge-m3\",\"input\":\"\",\"keep_alive\":\"10m\"}"))));
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/generate")));
	}

	@Test
	void naoCarregaModeloQueNaoEstaInstalado() {
		assertThatThrownBy(() -> gerente().warmUp("strong")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_NOT_INSTALLED);
			assertThat(e.getMessage()).contains("qwen2.5:14b").contains("Baixe-o antes");
		});
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/generate")));
	}

	@Test
	void naoCarregaModeloQueNaoCabeNestaMaquina() {
		assertThatThrownBy(() -> gerente().warmUp("extra")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MODEL_DOES_NOT_FIT));
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/generate")));
	}

	@Test
	void carregarSemOllamaEOllamaUnavailable() throws IOException {
		ModelManagerService semOllama = gerente(DESENVOLVIMENTO, "http://localhost:" + portaFechada(), ModelsTestData.tiers());

		assertThatThrownBy(() -> semOllama.warmUp("medium")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE));
	}

	@Test
	void carregarIdInexistenteEhNotFound() {
		assertThatThrownBy(() -> gerente().warmUp("nope")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void falhaAoCarregarDepoisDaRespostaNaoQuebraNadaELiberaParaTentarDeNovo() {
		padrao.stubFor(post("/api/generate").willReturn(aResponse().withStatus(500)
			.withBody("{\"error\":\"model requires more system memory (9.1 GiB) than is available (2.0 GiB)\"}")));
		ModelManagerService gerente = gerente();

		gerente.warmUp("medium");
		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> padrao.verify(1, postRequestedFor(urlEqualTo("/api/generate"))));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			gerente.warmUp("medium");
			padrao.verify(2, postRequestedFor(urlEqualTo("/api/generate")));
		});
	}

	// ================================================================ descarregar

	@Test
	void descarregaOModeloQueEstaNaMemoria() {
		emMemoria(padrao, "qwen2.5:7b");
		padrao.stubFor(post("/api/generate").willReturn(json("{\"done\":true}")));

		gerente().unload("medium");

		padrao.verify(1, postRequestedFor(urlEqualTo("/api/generate"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:7b\",\"keep_alive\":0,\"stream\":false}")));
	}

	@Test
	void descarregarModeloDeEmbeddingUsaOEmbedEReconheceOLatest() {
		emMemoria(padrao, "bge-m3:latest");
		padrao.stubFor(post("/api/embed").willReturn(json("{\"embeddings\":[]}")));

		gerente().unload("embedding");

		padrao.verify(1, postRequestedFor(urlEqualTo("/api/embed"))
			.withRequestBody(equalToJson("{\"model\":\"bge-m3\",\"input\":\"\",\"keep_alive\":0}")));
	}

	@Test
	void descarregarQuemNaoEstaNaMemoriaNaoFazNadaEContaComoSucesso() {
		gerente().unload("low");
		gerente().unload("strong");

		padrao.verify(0, postRequestedFor(urlEqualTo("/api/generate")));
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/embed")));
	}

	@Test
	void descarregarSemOllamaEOllamaUnavailableEIdInexistenteENotFound() throws IOException {
		ModelManagerService semOllama = gerente(DESENVOLVIMENTO, "http://localhost:" + portaFechada(), ModelsTestData.tiers());

		assertThatThrownBy(() -> semOllama.unload("medium")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE));
		assertThatThrownBy(() -> gerente().unload("nope")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	// =================================================================== remover

	@Test
	void removeOModeloInstaladoQueNaoEstaNaMemoria() {
		padrao.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(200)));

		gerente().delete("low");

		padrao.verify(1, deleteRequestedFor(urlEqualTo("/api/delete"))
			.withRequestBody(equalToJson("{\"model\":\"qwen2.5:3b\"}")));
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/generate")));
	}

	@Test
	void modeloNaMemoriaEDescarregadoAntesDeSerRemovido() {
		emMemoria(padrao, "qwen2.5:7b");
		padrao.stubFor(post("/api/generate").willReturn(json("{\"done\":true}")));
		padrao.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(200)));

		gerente().delete("medium");

		// o WireMock lista do mais novo para o mais antigo; se dois pedidos caem no mesmo milissegundo, a ordenação
		// estável precisa partir da ordem de chegada, senão o empate inverte o resultado
		List<ServeEvent> chegada = new ArrayList<>(padrao.getAllServeEvents());
		Collections.reverse(chegada);
		List<String> ordem = chegada.stream()
			.sorted((a, b) -> a.getRequest().getLoggedDate().compareTo(b.getRequest().getLoggedDate()))
			.map(ModelManagerServiceTest::rotulo)
			.filter(r -> r.startsWith("POST /api/generate") || r.startsWith("DELETE"))
			.toList();
		assertThat(ordem).containsExactly("POST /api/generate", "DELETE /api/delete");
	}

	@Test
	void falhaAoDescarregarAntesDeRemoverNaoImpedeARemocao() {
		emMemoria(padrao, "qwen2.5:7b");
		padrao.stubFor(post("/api/generate").willReturn(aResponse().withStatus(500).withBody("{\"error\":\"x\"}")));
		padrao.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(200)));

		gerente().delete("medium");

		padrao.verify(1, deleteRequestedFor(urlEqualTo("/api/delete")));
	}

	@Test
	void removerModeloDeEmbeddingApagaOBgeM3() {
		padrao.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(200)));

		gerente().delete("embedding");

		padrao.verify(1, deleteRequestedFor(urlEqualTo("/api/delete"))
			.withRequestBody(equalToJson("{\"model\":\"bge-m3\"}")));
	}

	@Test
	void removerQuemNaoEstaInstaladoEhNotFoundESemChamarODelete() {
		assertThatThrownBy(() -> gerente().delete("strong")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
			assertThat(e.getMessage()).contains("qwen2.5:14b").contains("não está instalado");
		});
		padrao.verify(0, deleteRequestedFor(urlEqualTo("/api/delete")));
	}

	@Test
	void naoRemoveEnquantoOModeloEstaSendoBaixado() {
		tags(padrao, tag("qwen2.5:14b", 9_000_000_000L));
		pullLento(padrao);
		ModelManagerService gerente = gerente();
		assinaturas.add(gerente.pull("strong").subscribe(evento -> { }, erro -> { }));

		assertThatThrownBy(() -> gerente.delete("strong")).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.CONFLICT);
			assertThat(e.getMessage()).contains("sendo baixado");
		});
		padrao.verify(0, deleteRequestedFor(urlEqualTo("/api/delete")));
	}

	@Test
	void falhaDoOllamaAoRemoverViraModelException() {
		padrao.stubFor(delete("/api/delete").willReturn(aResponse().withStatus(500).withBody("{\"error\":\"boom\"}")));

		assertThatThrownBy(() -> gerente().delete("low")).isInstanceOf(ModelException.class);
	}

	@Test
	void removerSemOllamaEOllamaUnavailable() throws IOException {
		ModelManagerService semOllama = gerente(DESENVOLVIMENTO, "http://localhost:" + portaFechada(), ModelsTestData.tiers());

		assertThatThrownBy(() -> semOllama.delete("low")).isInstanceOfSatisfying(ModelException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE));
	}

	// ================================================================== auxiliares

	private ModelManagerService gerente() {
		return gerente(DESENVOLVIMENTO, padrao.baseUrl(), ModelsTestData.tiers());
	}

	private ModelManagerService gerente(HardwareInfo maquina, String ollamaPadrao, Map<Tier, TierProperties> tiers) {
		AppSettingRepository configuracoes = mock(AppSettingRepository.class);
		when(configuracoes.findById(anyString())).thenReturn(Optional.empty());
		ModelsProperties propriedades = ModelsTestData.properties(Tier.MEDIUM, tiers, ModelsTestData.embedding(null));
		OllamaHosts hosts = new OllamaHosts(ollamaPadrao);
		HardwareDetector hardware = () -> maquina;
		ModelCatalogService catalogo = new ModelCatalogService(propriedades, hosts, hardware, configuracoes);
		ModelManagerService gerente = new ModelManagerService(propriedades, hosts, catalogo, hardware);
		catalogos.add(catalogo);
		gerentes.add(gerente);
		return gerente;
	}

	private static String rotulo(ServeEvent evento) {
		return evento.getRequest().getMethod() + " " + evento.getRequest().getUrl();
	}

	private static int portaFechada() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String corpo) {
		return aResponse().withHeader("Content-Type", "application/json").withBody(corpo);
	}

	private static String tag(String nome, long bytes) {
		return "{\"name\":\"%s\",\"model\":\"%s\",\"size\":%d,\"digest\":\"d-%s\"}".formatted(nome, nome, bytes, nome);
	}

	private static void tags(WireMockServer servidor, String... modelos) {
		servidor.stubFor(get("/api/tags").willReturn(json("{\"models\":[" + String.join(",", modelos) + "]}")));
	}

	private static void emMemoria(WireMockServer servidor, String... modelos) {
		List<String> itens = new ArrayList<>();
		for (String modelo : modelos) {
			itens.add("{\"name\":\"%s\",\"size\":6000000000,\"size_vram\":0}".formatted(modelo));
		}
		servidor.stubFor(get("/api/ps").willReturn(json("{\"models\":[" + String.join(",", itens) + "]}")));
	}

	private static String camada(String digest, long total, long baixado) {
		return "{\"status\":\"pulling %s\",\"digest\":\"sha256:%s\",\"total\":%d,\"completed\":%d}".formatted(digest,
				digest, total, baixado);
	}

	private static void pull(WireMockServer servidor, String... linhas) {
		servidor.stubFor(post("/api/pull").willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
			.withBody(String.join("\n", linhas) + "\n")));
	}

	/** Um download que demora a responder: dá tempo de o teste mexer enquanto ele está "em andamento". */
	private static void pullLento(WireMockServer servidor) {
		servidor.stubFor(post("/api/pull").willReturn(aResponse().withFixedDelay(4000)
			.withHeader("Content-Type", "application/x-ndjson")
			.withBody(SUCCESS + "\n")));
	}
}
