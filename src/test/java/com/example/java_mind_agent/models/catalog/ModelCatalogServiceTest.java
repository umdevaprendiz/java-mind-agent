package com.example.java_mind_agent.models.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.common.persistence.AppSetting;
import com.example.java_mind_agent.common.persistence.AppSettingRepository;
import com.example.java_mind_agent.models.catalog.ModelInfo.Kind;
import com.example.java_mind_agent.models.catalog.ModelInfo.UnavailableReason;
import com.example.java_mind_agent.models.hardware.HardwareInfo;
import com.example.java_mind_agent.models.ModelsProperties;
import com.example.java_mind_agent.models.ModelsProperties.TierProperties;
import com.example.java_mind_agent.models.ollama.OllamaHosts;
import com.example.java_mind_agent.models.Speed;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.support.ModelsTestData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * O catálogo do seletor com dois Ollamas falsos (o padrão e um "remoto"), hardware simulado e o banco de configurações
 * simulado. Sem Spring, sem Docker.
 */
class ModelCatalogServiceTest {

	private static final long GIB = 1024L * 1024L * 1024L;

	/** A máquina de desenvolvimento: 15,76 GiB de RAM, só gráficos integrados Intel. */
	private static final HardwareInfo DESENVOLVIMENTO = new HardwareInfo(15.76, 2.5, "Intel(R) UHD Graphics", true);

	private static WireMockServer padrao;

	private static WireMockServer remoto;

	private final List<ModelCatalogService> criados = new ArrayList<>();

	private AppSettingRepository configuracoes;

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
		configuracoes = mock(AppSettingRepository.class);
		when(configuracoes.findById(anyString())).thenReturn(Optional.empty());

		// Instalados no Ollama padrão: Baixo (3B) e Médio (7B) e o de embedding; o Médio está na memória.
		tags(padrao, tag("qwen2.5:3b", 1_900_000_000L, "d3"), tag("qwen2.5:7b", 4_683_087_332L, "d7"),
				tag("bge-m3:latest", 1_157_672_605L, "de"));
		emMemoria(padrao, "qwen2.5:7b");
		capacidades(padrao, "qwen2.5:3b", "completion");
		capacidades(padrao, "qwen2.5:7b", "completion", "tools");
	}

	@AfterEach
	void encerra() {
		criados.forEach(ModelCatalogService::shutdown);
	}

	// ------------------------------------------------------------------ o caso normal

	@Test
	void montaOsQuatroTiersEOEmbeddingNaMaquinaDeDesenvolvimento() {
		ModelCatalog catalogo = servico(DESENVOLVIMENTO).catalog();

		assertThat(catalogo.hardware()).isEqualTo(DESENVOLVIMENTO);
		assertThat(catalogo.defaultTier()).isEqualTo(Tier.MEDIUM);
		assertThat(catalogo.tiers()).extracting(ModelInfo::id).containsExactly("low", "medium", "strong", "extra");
		assertThat(catalogo.tiers()).extracting(ModelInfo::label).containsExactly("Baixo", "Médio", "Forte", "Extra");
		assertThat(catalogo.tiers()).extracting(ModelInfo::kind).containsOnly(Kind.CHAT);
		assertThat(catalogo.tiers()).extracting(ModelInfo::speed)
			.containsExactly(Speed.FAST, Speed.SLOW, Speed.VERY_SLOW, Speed.VERY_SLOW);
	}

	@Test
	void tierInstaladoQueCabeEstaDisponivelComTamanhoECapacidades() {
		ModelInfo baixo = tier(servico(DESENVOLVIMENTO).catalog(), "low");

		assertThat(baixo.installed()).isTrue();
		assertThat(baixo.fits()).isTrue();
		assertThat(baixo.available()).isTrue();
		assertThat(baixo.unavailableReason()).isNull();
		assertThat(baixo.loaded()).isFalse();
		assertThat(baixo.sizeGb()).isCloseTo(1.77, within(0.005));
		assertThat(baixo.diskUsageGb()).isEqualTo(baixo.sizeGb());
		assertThat(baixo.supportsTools()).as("o 3B só informa 'completion'").isFalse();
		assertThat(baixo.remoteHost()).isNull();
		assertThat(baixo.ollamaModel()).isEqualTo("qwen2.5:3b");
	}

	@Test
	void modeloNaMemoriaVemComoCarregadoESuportandoFerramentas() {
		ModelInfo medio = tier(servico(DESENVOLVIMENTO).catalog(), "medium");

		assertThat(medio.loaded()).isTrue();
		assertThat(medio.supportsTools()).isTrue();
		assertThat(medio.sizeGb()).isCloseTo(4.36, within(0.005));
		assertThat(medio.recommended()).isTrue();
	}

	@Test
	void tierQueCabeMasNaoFoiBaixadoFicaIndisponivelPorNaoEstarInstalado() {
		ModelInfo forte = tier(servico(DESENVOLVIMENTO).catalog(), "strong");

		assertThat(forte.fits()).isTrue();
		assertThat(forte.installed()).isFalse();
		assertThat(forte.available()).isFalse();
		assertThat(forte.unavailableReason()).isEqualTo(UnavailableReason.NOT_INSTALLED);
		assertThat(forte.sizeGb()).isNull();
		assertThat(forte.supportsTools()).isNull();
		assertThat(forte.loaded()).isFalse();
	}

	@Test
	void tierQueNaoCabeNestaMaquinaEInviavelMesmoSemEstarInstalado() {
		ModelInfo extra = tier(servico(DESENVOLVIMENTO).catalog(), "extra");

		assertThat(extra.fits()).isFalse();
		assertThat(extra.available()).isFalse();
		assertThat(extra.unavailableReason()).as("não caber vale mais que não estar instalado")
			.isEqualTo(UnavailableReason.DOES_NOT_FIT);
		assertThat(extra.minRamGb()).isEqualTo(26);
	}

	@Test
	void usaOTamanhoRealDoModeloParaDecidirSeCabe() {
		// O mínimo configurado do Baixo é 2,5 GB, mas o que está instalado ocupa 20 GiB: 20 × 1,3 = 26 > 12,76.
		tags(padrao, tag("qwen2.5:3b", 20 * GIB, "d3"));

		ModelInfo baixo = tier(servico(DESENVOLVIMENTO).catalog(), "low");

		assertThat(baixo.installed()).isTrue();
		assertThat(baixo.fits()).isFalse();
		assertThat(baixo.unavailableReason()).isEqualTo(UnavailableReason.DOES_NOT_FIT);
	}

	@Test
	void embeddingReconheceBgeM3ComoBgeM3Latest() {
		ModelInfo embedding = servico(DESENVOLVIMENTO).catalog().embedding();

		assertThat(embedding.id()).isEqualTo("embedding");
		assertThat(embedding.kind()).isEqualTo(Kind.EMBEDDING);
		assertThat(embedding.ollamaModel()).isEqualTo("bge-m3");
		assertThat(embedding.installed()).isTrue();
		assertThat(embedding.available()).isTrue();
		assertThat(embedding.supportsTools()).as("embedding não conversa").isNull();
		assertThat(embedding.recommended()).isTrue();
	}

	// -------------------------------------------------------------- Ollama fora do ar

	@Test
	void ollamaForaDoArNaoDerrubaOCatalogoEMarcaTudoComoHostInalcancavel() throws IOException {
		ModelCatalogService semOllama = servico(DESENVOLVIMENTO, "http://localhost:" + portaFechada(),
				ModelsTestData.tiers());

		ModelCatalog catalogo = semOllama.catalog();

		assertThat(catalogo.hardware()).as("a máquina continua informada").isEqualTo(DESENVOLVIMENTO);
		assertThat(catalogo.tiers()).hasSize(4);
		assertThat(catalogo.tiers()).allSatisfy(t -> {
			assertThat(t.available()).isFalse();
			assertThat(t.installed()).isFalse();
			assertThat(t.loaded()).isFalse();
			assertThat(t.supportsTools()).isNull();
			assertThat(t.unavailableReason()).isEqualTo(UnavailableReason.HOST_UNREACHABLE);
		});
		assertThat(catalogo.embedding().unavailableReason()).isEqualTo(UnavailableReason.HOST_UNREACHABLE);
		assertThat(catalogo.tiers().get(3).fits()).as("o Extra continua não cabendo, com ou sem Ollama").isFalse();
	}

	@Test
	void listaDeCarregadosQueFalhaNaoImpedeOCatalogo() {
		padrao.stubFor(get("/api/ps").willReturn(aResponse().withStatus(500)));

		ModelCatalog catalogo = servico(DESENVOLVIMENTO).catalog();

		assertThat(tier(catalogo, "medium").available()).isTrue();
		assertThat(tier(catalogo, "medium").loaded()).isFalse();
	}

	@Test
	void capacidadesQueFalhamViramDesconhecidasSemTornarOModeloIndisponivel() {
		padrao.stubFor(post("/api/show").willReturn(aResponse().withStatus(500)));

		ModelInfo medio = tier(servico(DESENVOLVIMENTO).catalog(), "medium");

		assertThat(medio.supportsTools()).isNull();
		assertThat(medio.available()).isTrue();
	}

	// ---------------------------------------------------------------- host remoto

	@Test
	void tierComBaseUrlPropriaConsultaOOutroOllamaECabePorPresuncao() {
		tags(remoto, tag("qwen3:32b", 20 * GIB, "r32"));
		capacidades(remoto, "qwen3:32b", "completion", "tools", "thinking");
		ModelCatalogService servico = servico(DESENVOLVIMENTO, padrao.baseUrl(), comExtraRemoto(remoto.baseUrl()));

		ModelInfo extra = tier(servico.catalog(), "extra");

		assertThat(extra.remoteHost()).isEqualTo(remoto.baseUrl());
		assertThat(extra.fits()).as("a RAM do outro computador não é conhecida").isTrue();
		assertThat(extra.installed()).isTrue();
		assertThat(extra.available()).isTrue();
		assertThat(extra.unavailableReason()).isNull();
		assertThat(extra.supportsTools()).isTrue();
		assertThat(extra.sizeGb()).isCloseTo(20.0, within(0.005));
		padrao.verify(0, postRequestedFor(urlEqualTo("/api/show"))
			.withRequestBody(matchingJsonPath("$.model", equalTo("qwen3:32b"))));
	}

	@Test
	void hostRemotoForaDoArAfetaSoOsTiersDele() throws IOException {
		ModelCatalogService servico = servico(DESENVOLVIMENTO, padrao.baseUrl(),
				comExtraRemoto("http://localhost:" + portaFechada()));

		ModelCatalog catalogo = servico.catalog();

		assertThat(tier(catalogo, "extra").unavailableReason()).isEqualTo(UnavailableReason.HOST_UNREACHABLE);
		assertThat(tier(catalogo, "extra").remoteHost()).startsWith("http://localhost:");
		assertThat(tier(catalogo, "low").available()).isTrue();
		assertThat(tier(catalogo, "medium").available()).isTrue();
	}

	@Test
	void doisTiersNoMesmoHostGeramUmaConsultaSoPorHost() {
		tags(remoto, tag("qwen2.5:14b", 9 * GIB, "r14"), tag("qwen3:32b", 20 * GIB, "r32"));
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.STRONG, ModelsTestData.tier("Forte", "qwen2.5:14b", 12, Speed.VERY_SLOW, remoto.baseUrl()));
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, remoto.baseUrl() + "/"));

		servico(DESENVOLVIMENTO, padrao.baseUrl(), tiers).catalog();

		remoto.verify(1, getRequestedFor(urlEqualTo("/api/tags")));
		padrao.verify(1, getRequestedFor(urlEqualTo("/api/tags")));
	}

	@Test
	void consultaOsHostsEmParaleloEntaoOSegundoNaoEsperaOPrimeiroTerminar() {
		int atrasoMs = 1500;
		for (WireMockServer host : List.of(padrao, remoto)) {
			host.stubFor(get("/api/tags").willReturn(aResponse().withFixedDelay(atrasoMs)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"models\":[]}")));
		}
		ModelCatalogService servico = servico(DESENVOLVIMENTO, padrao.baseUrl(), comExtraRemoto(remoto.baseUrl()));

		servico.catalog();

		// Mede quando cada host RECEBEU o pedido, não quanto o teste demorou: em série o segundo só chegaria depois
		// de o primeiro responder (≥ 1,5 s de diferença); em paralelo chegam com milissegundos de diferença.
		long diferenca = Math.abs(chegada(padrao) - chegada(remoto));
		assertThat(diferenca).as("ms entre os dois pedidos").isLessThan(atrasoMs / 2);
	}

	// ----------------------------------------------------------------- recomendação

	@Test
	void recomendaOTierPadraoQuandoCabe() {
		ModelCatalog catalogo = servico(DESENVOLVIMENTO).catalog();

		assertThat(catalogo.tiers()).filteredOn(ModelInfo::recommended).extracting(ModelInfo::id)
			.containsExactly("medium");
	}

	@Test
	void recomendaOMaisForteAbaixoDoPadraoQuandoOPadraoNaoCabe() {
		// 8 GiB de RAM: sobram 5 GiB depois da reserva; o Médio pede 6, o Baixo pede 2,5.
		HardwareInfo pequena = new HardwareInfo(8, 2, "Intel(R) UHD Graphics", true);

		ModelCatalog catalogo = servico(pequena).catalog();

		assertThat(tier(catalogo, "medium").fits()).isFalse();
		assertThat(catalogo.tiers()).filteredOn(ModelInfo::recommended).extracting(ModelInfo::id)
			.containsExactly("low");
	}

	@Test
	void naoRecomendaNadaQuandoNemOBaixoCabe() {
		HardwareInfo minuscula = new HardwareInfo(4, 1, null, true);

		ModelCatalog catalogo = servico(minuscula).catalog();

		assertThat(catalogo.tiers()).noneMatch(ModelInfo::recommended);
		assertThat(catalogo.tiers()).noneMatch(ModelInfo::available);
	}

	@Test
	void naoRecomendaTierMaisForteQueOPadraoMesmoQueCaiba() {
		HardwareInfo grande = new HardwareInfo(64, 40, "NVIDIA GeForce RTX 4090", false);

		ModelCatalog catalogo = servico(grande).catalog();

		assertThat(catalogo.tiers()).allMatch(ModelInfo::fits);
		assertThat(catalogo.tiers()).filteredOn(ModelInfo::recommended).extracting(ModelInfo::id)
			.containsExactly("medium");
	}

	// ------------------------------------------------------------------ tier padrão

	@Test
	void tierPadraoSalvoNoBancoVenceAConfiguracao() {
		when(configuracoes.findById(ModelCatalogService.DEFAULT_TIER_SETTING))
			.thenReturn(Optional.of(new AppSetting(ModelCatalogService.DEFAULT_TIER_SETTING, "strong")));

		ModelCatalog catalogo = servico(DESENVOLVIMENTO).catalog();

		assertThat(catalogo.defaultTier()).isEqualTo(Tier.STRONG);
		assertThat(catalogo.tiers()).filteredOn(ModelInfo::recommended).extracting(ModelInfo::id)
			.containsExactly("strong");
	}

	@Test
	void valorInvalidoNoBancoCaiParaAConfiguracao() {
		when(configuracoes.findById(ModelCatalogService.DEFAULT_TIER_SETTING))
			.thenReturn(Optional.of(new AppSetting(ModelCatalogService.DEFAULT_TIER_SETTING, "ultra")));

		assertThat(servico(DESENVOLVIMENTO).catalog().defaultTier()).isEqualTo(Tier.MEDIUM);
	}

	@Test
	void bancoForaDoArNaoImpedeOCatalogo() {
		when(configuracoes.findById(anyString())).thenThrow(new IllegalStateException("sem banco"));

		assertThat(servico(DESENVOLVIMENTO).catalog().defaultTier()).isEqualTo(Tier.MEDIUM);
	}

	// ---------------------------------------------------------- cache de capacidades

	@Test
	void capacidadesDeUmModeloSoSaoPerguntadasUmaVezEnquantoOConteudoNaoMuda() {
		ModelCatalogService servico = servico(DESENVOLVIMENTO);

		servico.catalog();
		servico.catalog();
		servico.catalog();

		padrao.verify(2, postRequestedFor(urlEqualTo("/api/show")));
		padrao.verify(3, getRequestedFor(urlEqualTo("/api/tags")));
	}

	@Test
	void modeloAtualizadoComOutroDigestTemSuasCapacidadesPerguntadasDeNovo() {
		ModelCatalogService servico = servico(DESENVOLVIMENTO);
		servico.catalog();

		tags(padrao, tag("qwen2.5:3b", 1_900_000_000L, "d3"), tag("qwen2.5:7b", 4_683_087_332L, "d7-novo"),
				tag("bge-m3:latest", 1_157_672_605L, "de"));
		servico.catalog();

		padrao.verify(3, postRequestedFor(urlEqualTo("/api/show")));
	}

	@Test
	void respostaSemCapacidadesNaoFicaGuardadaParaSerPerguntadaDeNovo() {
		padrao.stubFor(post("/api/show").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"details\":{}}")));
		ModelCatalogService servico = servico(DESENVOLVIMENTO);

		servico.catalog();
		servico.catalog();

		padrao.verify(4, postRequestedFor(urlEqualTo("/api/show")));
	}

	// ----------------------------------------------------------------- auxiliares

	private ModelCatalogService servico(HardwareInfo maquina) {
		return servico(maquina, padrao.baseUrl(), ModelsTestData.tiers());
	}

	private ModelCatalogService servico(HardwareInfo maquina, String ollamaPadrao, Map<Tier, TierProperties> tiers) {
		ModelsProperties propriedades = ModelsTestData.properties(Tier.MEDIUM, tiers, ModelsTestData.embedding(null));
		ModelCatalogService servico = new ModelCatalogService(propriedades, new OllamaHosts(ollamaPadrao),
				() -> maquina, configuracoes);
		criados.add(servico);
		return servico;
	}

	private static Map<Tier, TierProperties> comExtraRemoto(String baseUrl) {
		Map<Tier, TierProperties> tiers = ModelsTestData.tiers();
		tiers.put(Tier.EXTRA, ModelsTestData.tier("Extra", "qwen3:32b", 26, Speed.VERY_SLOW, baseUrl));
		return tiers;
	}

	/** Instante (ms) em que o servidor falso recebeu o pedido de {@code /api/tags}. */
	private static long chegada(WireMockServer servidor) {
		return servidor.getAllServeEvents().stream()
			.filter(e -> e.getRequest().getUrl().equals("/api/tags"))
			.mapToLong(e -> e.getRequest().getLoggedDate().getTime())
			.min()
			.orElseThrow();
	}

	private static ModelInfo tier(ModelCatalog catalogo, String id) {
		return catalogo.tiers().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
	}

	private static int portaFechada() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static String tag(String nome, long bytes, String digest) {
		return "{\"name\":\"%s\",\"model\":\"%s\",\"size\":%d,\"digest\":\"%s\"}".formatted(nome, nome, bytes, digest);
	}

	private static void tags(WireMockServer servidor, String... modelos) {
		servidor.stubFor(get("/api/tags").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"models\":[" + String.join(",", modelos) + "]}")));
	}

	private static void emMemoria(WireMockServer servidor, String modelo) {
		servidor.stubFor(get("/api/ps").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"models\":[{\"name\":\"%s\",\"size\":6000000000,\"size_vram\":0}]}".formatted(modelo))));
	}

	private static void capacidades(WireMockServer servidor, String modelo, String... capacidades) {
		String lista = String.join(",", java.util.Arrays.stream(capacidades).map(c -> "\"" + c + "\"").toList());
		servidor.stubFor(post("/api/show").withRequestBody(matchingJsonPath("$.model", equalTo(modelo)))
			.willReturn(aResponse().withHeader("Content-Type", "application/json")
				.withBody("{\"capabilities\":[" + lista + "]}")));
	}
}
