package com.example.java_mind_agent.knowledge;

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * A política de versão do agente: <b>o que a pessoa disse &gt; a versão escolhida para a conversa &gt; a versão que o
 * código exige &gt; a preferência salva &gt; a LTS mais recente</b>. É o ponto que o núcleo do agente (F15) chama a cada
 * pergunta; a resposta sempre declara a versão assumida ({@link AssumedVersion#statement}).
 */
@Service
public class JavaVersionService {

	private final JavaVersionRegistry registry;

	private final JavaVersionDetector detector;

	private final int defaultVersion;

	private final int legacyVersion;

	public JavaVersionService(JavaVersionRegistry registry, JavaVersionDetector detector, KnowledgeProperties properties) {
		this.registry = registry;
		this.detector = detector;
		Integer configurada = properties.defaultJavaVersion();
		this.defaultVersion = configurada != null ? exige(configurada, "app.knowledge.default-java-version")
				: registry.latestLts().version();
		this.legacyVersion = exige(properties.legacyJavaVersion(), "app.knowledge.legacy-java-version");
	}

	/** As versões conhecidas, da mais antiga para a mais nova. */
	public List<JavaVersionInfo> versions() {
		return registry.infos();
	}

	public VersionDetection detect(@Nullable String text) {
		return detector.detect(text);
	}

	/** A versão assumida quando nada indica outra. */
	public int defaultVersion() {
		return defaultVersion;
	}

	/**
	 * @param text            a pergunta (e o código colado nela)
	 * @param selected        a versão fixada para a conversa; {@code null} = automático. Uma versão fora do registro é
	 *                        ignorada
	 * @param settingsDefault {@code defaultJavaVersion} das preferências; {@code null} = automático
	 */
	public AssumedVersion assume(@Nullable String text, @Nullable Integer selected, @Nullable Integer settingsDefault) {
		VersionDetection detection = detector.detect(text);
		List<Integer> mentioned = detection.mentioned();
		int version;
		VersionSource source;
		if (!mentioned.isEmpty()) {
			version = Collections.max(mentioned);
			source = VersionSource.MENTIONED;
		}
		else if (detection.legacyHint()) {
			version = legacyVersion;
			source = VersionSource.LEGACY_HINT;
		}
		else if (detection.latestHint()) {
			version = registry.latest().version();
			source = VersionSource.LATEST_HINT;
		}
		else if (known(selected)) {
			version = selected;
			source = VersionSource.SELECTED;
		}
		else if (detection.inferredMin().isPresent()) {
			version = detection.inferredMin().get();
			source = VersionSource.INFERRED;
		}
		else if (known(settingsDefault)) {
			version = settingsDefault;
			source = VersionSource.SETTINGS;
		}
		else {
			version = defaultVersion;
			source = VersionSource.DEFAULT;
		}
		boolean inferred = source == VersionSource.INFERRED;
		return new AssumedVersion(version, registry.find(version).orElseThrow().label(), source, inferred, mentioned,
				inferred ? detection.evidence() : List.of(),
				inferred ? List.of() : detection.requiringMoreThan(version),
				detection.previewFeatures());
	}

	private boolean known(@Nullable Integer version) {
		return version != null && registry.contains(version);
	}

	private int exige(int version, String propriedade) {
		if (!registry.contains(version)) {
			throw new IllegalStateException(propriedade + "=" + version + " não está no registro de versões do Java");
		}
		return version;
	}
}
