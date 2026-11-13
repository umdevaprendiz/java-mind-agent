package com.example.java_mind_agent.models;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.java_mind_agent.common.ErrorCode;
import com.example.java_mind_agent.models.ModelInfo.Kind;

import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Baixa, carrega, descarrega e remove os modelos dos tiers (e o de embedding) no Ollama de cada um, sem o usuário abrir
 * o terminal. O {@code id} é o do contrato: {@code low}, {@code medium}, {@code strong}, {@code extra} ou
 * {@code embedding}.
 *
 * <p>Regras que valem para tudo aqui:
 * <ul>
 * <li>nunca age sobre um modelo que não é de um tier configurado (o id vem da configuração, nunca de texto livre);</li>
 * <li>um modelo que não cabe nesta máquina não é baixado nem carregado (baixar 20 GB para não conseguir rodar é só
 * desperdício de disco);</li>
 * <li>duas operações destrutivas ou pesadas no mesmo modelo não se atropelam (dois downloads, ou remover enquanto
 * baixa).</li>
 * </ul>
 */
@Service
public class ModelManagerService {

	private static final Logger log = LoggerFactory.getLogger(ModelManagerService.class);

	private final ModelsProperties properties;

	private final OllamaHosts hosts;

	private final ModelCatalogService catalog;

	private final HardwareDetector hardware;

	/** Downloads em andamento, por host + modelo (dois tiers podem apontar para o mesmo modelo). */
	private final Set<String> pulling = ConcurrentHashMap.newKeySet();

	/** Carregamentos na memória em andamento; repetir o pedido enquanto carrega não carrega duas vezes. */
	private final Set<String> warming = ConcurrentHashMap.newKeySet();

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public ModelManagerService(ModelsProperties properties, OllamaHosts hosts, ModelCatalogService catalog,
			HardwareDetector hardware) {
		this.properties = properties;
		this.hosts = hosts;
		this.catalog = catalog;
		this.hardware = hardware;
	}

	@PreDestroy
	void shutdown() {
		// Um carregamento em CPU pode levar minutos; ao desligar o app não vale esperar por ele.
		executor.shutdownNow();
	}

	// ------------------------------------------------------------------ baixar

	/**
	 * Baixa o modelo do id, devolvendo o progresso. Os problemas que dá para saber antes de começar (id inexistente,
	 * Ollama fora do ar, modelo que não cabe, download já em andamento) são lançados aqui, logo, para o controller
	 * responder um erro HTTP; o que acontecer depois vai como erro dentro do stream.
	 *
	 * <p>Cancelar a assinatura cancela o download no Ollama, que guarda o que já baixou: baixar de novo continua de
	 * onde parou. Baixar um modelo já instalado serve para atualizá-lo.
	 *
	 * @throws ModelException {@code NOT_FOUND}, {@code OLLAMA_UNAVAILABLE}, {@code MODEL_DOES_NOT_FIT} ou {@code CONFLICT}
	 */
	public Flux<PullEvent> pull(String modelId) {
		ModelSpec spec = spec(modelId);
		ModelInfo info = catalog.describe(spec);
		if (info.unavailableReason() == ModelInfo.UnavailableReason.HOST_UNREACHABLE) {
			throw unreachable(spec);
		}
		if (!info.fits()) {
			throw doesNotFit(spec);
		}
		String key = key(spec);
		if (pulling.contains(key)) {
			throw alreadyPulling(spec);
		}

		OllamaAdminClient client = hosts.client(spec.host());
		ModelsProperties.Pull settings = properties.pull();
		// O lugar é reservado só ao assinar, de forma atômica: um Flux que ninguém assina não deixa o modelo preso, e dois
		// pedidos simultâneos não passam juntos pela checagem acima.
		return Flux.defer(() -> {
			if (!pulling.add(key)) {
				return Flux.<PullEvent>error(alreadyPulling(spec));
			}
			PullProgressAggregator progress = new PullProgressAggregator(spec.model(), settings.progressInterval());
			// O doFinally do Reactor chama "cancel" quando o consumidor encerra durante o onComplete; por isso o
			// desfecho para o log vem dos doOn*, que rodam antes. Sem nenhum deles, foi cancelamento mesmo.
			AtomicReference<String> outcome = new AtomicReference<>("cancelado");
			return client.pull(spec.model(), settings.idleTimeout())
				.<PullEvent>handle((line, sink) -> {
					try {
						progress.accept(line, System.nanoTime()).ifPresent(sink::next);
					}
					catch (ModelException e) {
						sink.error(e);
					}
				})
				.concatWith(Mono.fromSupplier(progress::finish))
				.onErrorMap(error -> OllamaErrorMapper.mapPull(spec.model(), error))
				.doOnComplete(() -> outcome.set("concluído"))
				.doOnError(error -> outcome.set("com erro: " + error.getMessage()))
				.doFinally(signal -> {
					pulling.remove(key);
					log.info("Download de {} ({}) terminou: {}", spec.model(), spec.id(), outcome.get());
				});
		});
	}

	// -------------------------------------------------------------- memória

	/**
	 * Pede ao Ollama que carregue o modelo na memória e responde logo, sem esperar (em CPU, um 7B leva ~40 s). Quem
	 * quiser acompanhar olha {@code loaded} em {@code GET /api/models}. Pedir de novo enquanto carrega não faz nada.
	 *
	 * @throws ModelException {@code NOT_FOUND}, {@code OLLAMA_UNAVAILABLE}, {@code MODEL_DOES_NOT_FIT} ou
	 *                        {@code MODEL_NOT_INSTALLED}
	 */
	public void warmUp(String modelId) {
		ModelSpec spec = spec(modelId);
		ModelInfo info = catalog.describe(spec);
		if (info.unavailableReason() != null) {
			throw switch (info.unavailableReason()) {
				case HOST_UNREACHABLE -> unreachable(spec);
				case DOES_NOT_FIT -> doesNotFit(spec);
				case NOT_INSTALLED -> notInstalled(spec);
			};
		}
		String key = key(spec);
		if (!warming.add(key)) {
			return;
		}
		OllamaAdminClient client = hosts.client(spec.host());
		boolean embedding = spec.kind() == Kind.EMBEDDING;
		executor.execute(() -> {
			try {
				client.load(spec.model(), embedding, properties.generation().keepAlive(),
						properties.pull().warmupTimeout());
				log.info("Modelo {} ({}) carregado na memória.", spec.model(), spec.id());
			}
			catch (ModelException e) {
				log.warn("Não consegui carregar {} ({}) na memória: {}", spec.model(), spec.id(), e.getMessage());
			}
			finally {
				warming.remove(key);
			}
		});
	}

	/** Tira o modelo da memória. Se já não estava carregado, não faz nada (o resultado é o mesmo que o pedido). */
	public void unload(String modelId) {
		ModelSpec spec = spec(modelId);
		OllamaAdminClient client = hosts.client(spec.host());
		if (isLoaded(client, spec)) {
			client.unload(spec.model(), spec.kind() == Kind.EMBEDDING);
		}
	}

	// ---------------------------------------------------------------- remover

	/**
	 * Remove o modelo do disco do Ollama (descarregando-o antes, se estiver na memória). Para trazer de volta, é
	 * preciso baixar de novo.
	 *
	 * @throws ModelException {@code NOT_FOUND} (id inexistente ou modelo não instalado), {@code CONFLICT} (baixando
	 *                        agora) ou {@code OLLAMA_UNAVAILABLE}
	 */
	public void delete(String modelId) {
		ModelSpec spec = spec(modelId);
		if (pulling.contains(key(spec))) {
			throw new ModelException(ErrorCode.CONFLICT, "O modelo '" + spec.model() + "' está sendo baixado agora. "
					+ "Cancele o download antes de removê-lo.", false);
		}
		OllamaAdminClient client = hosts.client(spec.host());
		boolean installed = client.listInstalled()
			.stream()
			.anyMatch(model -> ModelSpec.normalize(model.name()).equals(spec.normalizedModel()));
		if (!installed) {
			throw new ModelException(ErrorCode.NOT_FOUND,
					"O modelo '" + spec.model() + "' não está instalado; não há o que remover.", false);
		}
		if (isLoaded(client, spec)) {
			try {
				client.unload(spec.model(), spec.kind() == Kind.EMBEDDING);
			}
			catch (ModelException e) {
				log.warn("Não consegui descarregar {} antes de removê-lo; sigo com a remoção: {}", spec.model(),
						e.getMessage());
			}
		}
		client.delete(spec.model());
		log.info("Modelo {} ({}) removido do disco.", spec.model(), spec.id());
	}

	// -------------------------------------------------------------- auxiliares

	private ModelSpec spec(String modelId) {
		return catalog.spec(modelId)
			.orElseThrow(() -> new ModelException(ErrorCode.NOT_FOUND,
					"Não existe o modelo '" + modelId + "'. Use low, medium, strong, extra ou embedding.", false));
	}

	private static boolean isLoaded(OllamaAdminClient client, ModelSpec spec) {
		return client.listLoaded()
			.stream()
			.anyMatch(model -> ModelSpec.normalize(model.name()).equals(spec.normalizedModel()));
	}

	private static String key(ModelSpec spec) {
		return spec.host() + "|" + spec.normalizedModel();
	}

	private static ModelException unreachable(ModelSpec spec) {
		return new ModelException(ErrorCode.OLLAMA_UNAVAILABLE,
				"Não consegui falar com o Ollama em " + spec.host() + ". Confira se ele está em execução.", true);
	}

	private ModelException doesNotFit(ModelSpec spec) {
		double usable = Math.max(0, hardware.detect().totalRamGb() - properties.capacity().reservedRamGb());
		return new ModelException(ErrorCode.MODEL_DOES_NOT_FIT,
				"O modelo '%s' (%s) pede ~%s GB de RAM e esta máquina tem %s GB utilizáveis. Escolha um nível menor ou "
					.formatted(spec.model(), spec.label(), gb(spec.minRamGb()), gb(usable))
						+ "aponte esse nível para outro Ollama (base-url).",
				false);
	}

	private static ModelException notInstalled(ModelSpec spec) {
		return new ModelException(ErrorCode.MODEL_NOT_INSTALLED,
				"O modelo '" + spec.model() + "' (" + spec.label() + ") não está instalado. Baixe-o antes.", false);
	}

	private static ModelException alreadyPulling(ModelSpec spec) {
		return new ModelException(ErrorCode.CONFLICT,
				"O modelo '" + spec.model() + "' já está sendo baixado. Acompanhe o download em andamento.", false);
	}

	private static String gb(double value) {
		return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value);
	}
}
