package com.example.java_mind_agent.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.example.java_mind_agent.agent.loop.AgentLoop;
import com.example.java_mind_agent.agent.loop.LoopInput;
import com.example.java_mind_agent.agent.prompt.PromptContext;
import com.example.java_mind_agent.agent.prompt.SystemPromptBuilder;
import com.example.java_mind_agent.agent.tool.ToolRegistry;
import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.knowledge.version.JavaVersionService;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.GenerationOptions;
import com.example.java_mind_agent.models.routing.ModelRouter;
import com.example.java_mind_agent.models.routing.Route;
import com.example.java_mind_agent.settings.Language;
import com.example.java_mind_agent.settings.Settings;
import com.example.java_mind_agent.settings.SettingsService;

/**
 * A porta de entrada do agente: recebe uma pergunta e devolve uma execução pronta para transmitir.
 *
 * <p>{@link #prepare} faz tudo que pode falhar <em>antes</em> do stream começar: escolhe o modelo do nível pedido (e
 * confere que ele está instalado, cabe e o Ollama responde), decide a versão do Java (F10) e monta o prompt. Só
 * depois {@link AgentRun#events()} gera a resposta. O agente fala com um modelo só, o do nível pedido: nunca troca de
 * nível sozinho.
 *
 * <p>Ferramentas só são oferecidas a modelos que as suportam; os outros respondem sem elas, com um aviso de status.
 */
@Service
public class AgentService {

	private final ModelRouter router;

	private final SettingsService settings;

	private final JavaVersionService javaVersions;

	private final SystemPromptBuilder prompts;

	private final ToolRegistry tools;

	private final AgentLoop loop;

	private final AgentProperties properties;

	public AgentService(ModelRouter router, SettingsService settings, JavaVersionService javaVersions,
			SystemPromptBuilder prompts, ToolRegistry tools, AgentLoop loop, AgentProperties properties) {
		this.router = router;
		this.settings = settings;
		this.javaVersions = javaVersions;
		this.prompts = prompts;
		this.tools = tools;
		this.loop = loop;
		this.properties = properties;
	}

	/**
	 * Prepara a execução de {@code request}.
	 *
	 * @throws ModelException {@code OLLAMA_UNAVAILABLE}, {@code MODEL_DOES_NOT_FIT} ou {@code MODEL_NOT_INSTALLED}
	 *                        (com {@code tier} e, se houver, {@code suggestedTier}), antes de qualquer geração
	 */
	public AgentRun prepare(AgentRequest request) {
		Settings preferences = settings.get();
		Language language = request.language() != null ? request.language() : preferences.language();

		Route route = router.resolve(request.tier());
		AssumedVersion javaVersion = javaVersions.assume(request.message(), request.javaVersion(),
				preferences.defaultJavaVersion());

		boolean modelUsesTools = Boolean.TRUE.equals(route.supportsTools());
		boolean toolsEnabled = modelUsesTools && !tools.isEmpty();
		String notice = !modelUsesTools && !tools.isEmpty() ? AgentTexts.of(language).noTools(route.model()) : null;

		String system = prompts.build(
				new PromptContext(language, request.message(), javaVersion, request.teaching(), toolsEnabled));
		List<Message> messages = new ArrayList<>(request.history().size() + 2);
		messages.add(new SystemMessage(system));
		messages.addAll(request.history());
		messages.add(new UserMessage(request.message()));

		// A resposta sempre declara a versão (F10). Modelos pequenos ignoram a instrução, então o agente a escreve.
		String preface = javaVersion.statement(language) + "\n\n";

		AgentLimits limits = request.limits() != null ? request.limits() : properties.limits();
		GenerationOptions generation = request.generation() != null ? request.generation()
				: GenerationOptions.defaults();
		return new AgentRun(route, javaVersion, toolsEnabled,
				loop.start(new LoopInput(route, messages, toolsEnabled, notice, preface, limits, generation, language)));
	}
}
