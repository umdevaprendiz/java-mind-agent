package com.example.java_mind_agent.models;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.java_mind_agent.common.AppSetting;
import com.example.java_mind_agent.common.AppSettingRepository;
import com.example.java_mind_agent.models.ModelInfo.Kind;
import com.example.java_mind_agent.models.ModelInfo.UnavailableReason;
import com.example.java_mind_agent.models.OllamaAdminClient.InstalledModel;

import jakarta.annotation.PreDestroy;

/**
 * Monta o catálogo do seletor: para cada tier, o modelo configurado, se está instalado e carregado, se cabe nesta
 * máquina, o que suporta e se pode ser usado agora. Cada host Ollama (o padrão e os que algum tier aponta com
 * {@code base-url}) é consultado em paralelo e com tempo curto; um host fora do ar não derruba o catálogo, só marca
 * os seus modelos como {@link UnavailableReason#HOST_UNREACHABLE}.
 */
@Service
public class ModelCatalogService {

	/** Chave em {@code app_setting} com o tier padrão escolhido pelo usuário (gravada pela F08). */
	public static final String DEFAULT_TIER_SETTING = "default.tier";

	private static final Logger log = LoggerFactory.getLogger(ModelCatalogService.class);

	private static final double GIB = 1024.0 * 1024.0 * 1024.0;

	private final ModelsProperties properties;

	private final OllamaHosts hosts;

	private final HardwareDetector hardware;

	private final AppSettingRepository settings;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/** O que cada modelo sabe fazer não muda enquanto o conteúdo (digest) é o mesmo; evita um {@code /api/show} por tela. */
	private final Map<String, List<String>> capabilitiesCache = new ConcurrentHashMap<>();

	public ModelCatalogService(ModelsProperties properties, OllamaHosts hosts, HardwareDetector hardware,
			AppSettingRepository settings) {
		this.properties = properties;
		this.hosts = hosts;
		this.hardware = hardware;
		this.settings = settings;
	}

	@PreDestroy
	void shutdown() {
		executor.close();
	}

	public ModelCatalog catalog() {
		HardwareInfo machine = hardware.detect();
		TierFitPolicy policy = new TierFitPolicy(properties.capacity());
		Tier defaultTier = defaultTier();

		List<Spec> chatSpecs = Arrays.stream(Tier.values())
			.map(tier -> Spec.chat(tier, properties.tiers().get(tier), hosts))
			.toList();
		Spec embeddingSpec = Spec.embedding(properties.embedding(), hosts);

		Map<String, CompletableFuture<HostSnapshot>> snapshots = new LinkedHashMap<>();
		Stream.concat(chatSpecs.stream(), Stream.of(embeddingSpec))
			.map(Spec::host)
			.distinct()
			.forEach(host -> snapshots.put(host, CompletableFuture.supplyAsync(() -> snapshot(host), executor)));

		List<ModelInfo> tiers = chatSpecs.stream()
			.map(spec -> info(spec, snapshots.get(spec.host()).join(), machine, policy))
			.toList();
		ModelInfo embedding = info(embeddingSpec, snapshots.get(embeddingSpec.host()).join(), machine, policy)
			.withRecommended(true);

		Tier recommended = recommendedTier(defaultTier, tiers);
		List<ModelInfo> marked = tiers.stream()
			.map(info -> info.withRecommended(recommended != null && info.id().equals(recommended.value())))
			.toList();
		return new ModelCatalog(machine, defaultTier, marked, embedding);
	}

	/** A preferência salva em {@code app_setting} vence a configuração; banco fora do ar não impede o catálogo. */
	Tier defaultTier() {
		try {
			return settings.findById(DEFAULT_TIER_SETTING)
				.map(AppSetting::getValue)
				.flatMap(Tier::find)
				.orElse(properties.defaultTier());
		}
		catch (RuntimeException e) {
			log.warn("Não consegui ler o tier padrão do banco; usando o da configuração: {}", e.getMessage());
			return properties.defaultTier();
		}
	}

	/**
	 * O tier que o app sugere para esta máquina: o padrão, se couber; senão o mais forte abaixo dele que couber;
	 * {@code null} se nem o Baixo couber.
	 */
	private static @Nullable Tier recommendedTier(Tier defaultTier, List<ModelInfo> tiers) {
		Tier[] all = Tier.values();
		for (int i = defaultTier.ordinal(); i >= 0; i--) {
			if (tiers.get(i).fits()) {
				return all[i];
			}
		}
		return null;
	}

	private ModelInfo info(Spec spec, HostSnapshot snapshot, HardwareInfo machine, TierFitPolicy policy) {
		boolean remote = !hosts.isDefault(spec.host());
		String name = normalize(spec.model());
		InstalledModel found = snapshot.installed().get(name);
		boolean installed = found != null;
		Double sizeGb = found != null ? round(found.size() / GIB) : null;
		boolean fits = remote || policy.fits(policy.requiredRamGb(spec.minRamGb(), sizeGb), machine);
		boolean loaded = snapshot.loaded().contains(name);
		Boolean supportsTools = found != null && spec.kind() == Kind.CHAT
				? supportsTools(spec.host(), spec.model(), found.digest()) : null;

		UnavailableReason reason = !snapshot.reachable() ? UnavailableReason.HOST_UNREACHABLE
				: !fits ? UnavailableReason.DOES_NOT_FIT : !installed ? UnavailableReason.NOT_INSTALLED : null;

		return new ModelInfo(spec.id(), spec.kind(), spec.label(), spec.description(), spec.model(),
				remote ? spec.host() : null, sizeGb, spec.minRamGb(), installed, loaded, fits, supportsTools,
				reason == null, reason, false, spec.speed(), null, sizeGb);
	}

	private @Nullable Boolean supportsTools(String host, String model, @Nullable String digest) {
		String key = host + "|" + model + "|" + digest;
		List<String> capabilities = capabilitiesCache.get(key);
		if (capabilities == null) {
			try {
				capabilities = hosts.client(host).capabilities(model);
			}
			catch (ModelException e) {
				log.debug("Não consegui ler as capacidades de {}: {}", model, e.getMessage());
				return null;
			}
			if (!capabilities.isEmpty()) {
				capabilitiesCache.put(key, capabilities);
			}
		}
		return capabilities.isEmpty() ? null : capabilities.contains("tools");
	}

	private HostSnapshot snapshot(String host) {
		OllamaAdminClient client = hosts.client(host);
		try {
			Map<String, InstalledModel> installed = new HashMap<>();
			client.listInstalled().forEach(model -> installed.put(normalize(model.name()), model));
			Set<String> loaded = new HashSet<>();
			try {
				client.listLoaded().forEach(model -> loaded.add(normalize(model.name())));
			}
			catch (ModelException e) {
				log.debug("Não consegui listar os modelos carregados em {}: {}", host, e.getMessage());
			}
			return new HostSnapshot(true, installed, loaded);
		}
		catch (ModelException e) {
			log.debug("Ollama em {} indisponível: {}", host, e.getMessage());
			return new HostSnapshot(false, Map.of(), Set.of());
		}
	}

	/** O Ollama trata {@code bge-m3} e {@code bge-m3:latest} como o mesmo modelo. */
	private static String normalize(String model) {
		return model.contains(":") ? model : model + ":latest";
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private record HostSnapshot(boolean reachable, Map<String, InstalledModel> installed, Set<String> loaded) {
	}

	/** O que o catálogo precisa saber de cada modelo antes de consultar o Ollama. */
	private record Spec(String id, Kind kind, String label, String description, String model, double minRamGb,
			String host, Speed speed) {

		static Spec chat(Tier tier, ModelsProperties.TierProperties tp, OllamaHosts hosts) {
			return new Spec(tier.value(), Kind.CHAT, tp.label(), tp.description(), tp.ollamaModel(), tp.minRamGb(),
					hosts.resolve(tp.baseUrl()), tp.speed());
		}

		static Spec embedding(ModelsProperties.Embedding e, OllamaHosts hosts) {
			return new Spec("embedding", Kind.EMBEDDING, e.label(), e.description(), e.model(), e.minRamGb(),
					hosts.resolve(e.baseUrl()), Speed.FAST);
		}
	}
}
