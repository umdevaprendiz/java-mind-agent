package com.example.java_mind_agent.models.routing;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.catalog.ModelCatalogService;
import com.example.java_mind_agent.models.catalog.ModelInfo;
import com.example.java_mind_agent.models.catalog.ModelInfo.UnavailableReason;
import com.example.java_mind_agent.models.catalog.ModelProblems;
import com.example.java_mind_agent.models.catalog.ModelSpec;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.ollama.ModelStreamEvent;
import com.example.java_mind_agent.models.ollama.OllamaChatService;
import com.example.java_mind_agent.models.Tier;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Decide qual modelo atende cada requisição, a partir do nível (tier) que ela traz — ou o padrão do usuário, quando
 * não traz — e o leva até o Ollama certo.
 *
 * <p><b>Nunca troca de nível em silêncio.</b> Se o nível pedido não pode ser usado agora (Ollama dele fora do ar, não
 * cabe nesta máquina, não está instalado), a requisição falha com um erro que diz o que fazer, e, quando existe, aponta
 * o {@code suggestedTier}: o nível menor que já dá para usar. Quem decide é o usuário, que reenvia com outro nível. Como
 * o histórico da conversa é parte da requisição (e não do modelo), reenviar com outro nível no meio da conversa
 * continua de onde estava.
 *
 * <p>Uso em duas etapas, para que as falhas conhecidas voltem como {@code problem+json} antes de o stream abrir:
 * {@link #resolve} e, depois, {@link #stream}.
 */
@Service
public class ModelRouter {

	private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

	private final ModelCatalogService catalog;

	private final OllamaChatService chat;

	public ModelRouter(ModelCatalogService catalog, OllamaChatService chat) {
		this.catalog = catalog;
		this.chat = chat;
	}

	/**
	 * Escolhe o modelo para {@code requested} ({@code null} = o nível padrão) e confere que ele pode responder agora.
	 * Consulta só o Ollama daquele nível.
	 *
	 * @throws ModelException {@code OLLAMA_UNAVAILABLE}, {@code MODEL_DOES_NOT_FIT} ou {@code MODEL_NOT_INSTALLED},
	 *                        com os campos {@code tier} e, se houver, {@code suggestedTier}
	 */
	public Route resolve(@Nullable Tier requested) {
		Tier tier = requested != null ? requested : catalog.defaultTier();
		ModelSpec spec = catalog.spec(tier.value())
			.orElseThrow(() -> new IllegalStateException("Nível sem configuração: " + tier));
		ModelInfo info = catalog.describe(spec);
		UnavailableReason reason = info.unavailableReason();
		if (reason != null) {
			ModelException problem = switch (reason) {
				case HOST_UNREACHABLE -> ModelProblems.unreachable(spec);
				case DOES_NOT_FIT -> ModelProblems.doesNotFit(spec, catalog.usableRamGb());
				case NOT_INSTALLED -> ModelProblems.notInstalled(spec);
			};
			throw withSuggestion(tier, problem);
		}
		Route route = new Route(tier, spec.label(), spec.model(), spec.host(), info.remoteHost() != null,
				info.loaded(), requested == null, info.supportsTools());
		log.debug("Requisição roteada para {} ({}){}", route.tier().value(), route.model(),
				route.remote() ? " em " + route.host() : "");
		return route;
	}

	/**
	 * Gera a resposta no modelo e no Ollama de {@code route}. O histórico vai em {@code messages} e não é tocado: o
	 * mesmo histórico serve a qualquer nível. Se o Ollama recusar por falta de memória na hora de gerar, o erro também
	 * traz o nível menor sugerido.
	 */
	public Flux<ModelStreamEvent> stream(Route route, List<Message> messages, GenerationOptions options) {
		return stream(route, messages, options, List.of());
	}

	/**
	 * Como {@link #stream(Route, List, GenerationOptions)}, oferecendo {@code tools} ao modelo; os pedidos de chamada
	 * chegam como {@code ToolCalls} e quem chama é que executa. Só passe ferramentas se
	 * {@link Route#supportsTools()} for {@code true}: o Ollama recusa ferramentas em modelo que não as suporta.
	 */
	public Flux<ModelStreamEvent> stream(Route route, List<Message> messages, GenerationOptions options,
			List<ToolCallback> tools) {
		return chat.stream(route.host(), route.model(), messages, options, tools)
			.onErrorResume(error -> isActionable(error)
					? Mono.fromCallable(() -> withSuggestion(route.tier(), (ModelException) error))
						.subscribeOn(Schedulers.boundedElastic())
						.flatMapMany(Flux::<ModelStreamEvent>error)
					: Flux.error(error));
	}

	private static boolean isActionable(Throwable error) {
		return error instanceof ModelException e
				&& (e.getCode() == ErrorCode.MODEL_DOES_NOT_FIT || e.getCode() == ErrorCode.MODEL_NOT_INSTALLED);
	}

	/**
	 * Acrescenta ao erro o nível que falhou e, se houver, um nível menor para usar no lugar. Prefere um já instalado
	 * (e que cabe); senão um que cabe mas precisa ser baixado. Só sugere: o app não muda de nível sozinho.
	 */
	private ModelException withSuggestion(Tier failed, ModelException error) {
		Optional<ModelInfo> installed = Optional.empty();
		Optional<ModelInfo> toDownload = Optional.empty();
		try {
			List<ModelInfo> tiers = catalog.catalog().tiers();
			for (int i = failed.ordinal() - 1; i >= 0; i--) {
				ModelInfo candidate = tiers.get(i);
				if (candidate.available()) {
					installed = Optional.of(candidate);
					break;
				}
				if (toDownload.isEmpty() && candidate.unavailableReason() == UnavailableReason.NOT_INSTALLED) {
					toDownload = Optional.of(candidate);
				}
			}
		}
		catch (RuntimeException e) {
			log.debug("Não consegui montar a sugestão de nível: {}", e.getMessage());
		}

		Optional<ModelInfo> suggestion = installed.isPresent() ? installed : toDownload;
		String message = error.getMessage();
		if (installed.isPresent()) {
			message += " O nível %s (%s) está instalado e cabe nesta máquina: escolha-o para responder agora."
				.formatted(installed.get().label(), installed.get().ollamaModel());
		}
		else if (toDownload.isPresent()) {
			message += " O nível %s (%s) cabe nesta máquina, mas precisa ser baixado antes."
				.formatted(toDownload.get().label(), toDownload.get().ollamaModel());
		}
		ModelException result = new ModelException(error.getCode(), message, error.isRetryable(), error.getCause());
		error.getDetails().forEach(result::detail);
		result.detail("tier", failed.value());
		suggestion.ifPresent(info -> result.detail("suggestedTier", info.id()));
		return result;
	}
}
