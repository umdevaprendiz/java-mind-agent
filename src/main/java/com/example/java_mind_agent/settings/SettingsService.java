package com.example.java_mind_agent.settings;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.java_mind_agent.common.persistence.AppSetting;
import com.example.java_mind_agent.common.persistence.AppSettingRepository;
import com.example.java_mind_agent.models.catalog.ModelCatalogService;

/**
 * Lê e grava as preferências em {@code app_setting}. O tier padrão tem uma única fonte de verdade, a que o roteador e
 * o catálogo já leem ({@link ModelCatalogService#DEFAULT_TIER_SETTING}); aqui só se escreve nela.
 */
@Service
public class SettingsService {

	static final String DEFAULT_JAVA_VERSION_KEY = "default.java-version";

	static final String LANGUAGE_KEY = "language";

	private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

	private final AppSettingRepository repository;

	private final ModelCatalogService catalog;

	public SettingsService(AppSettingRepository repository, ModelCatalogService catalog) {
		this.repository = repository;
		this.catalog = catalog;
	}

	/** O que está gravado; o que nunca foi gravado vale o padrão (tier da configuração, Java automático, português). */
	@Transactional(readOnly = true)
	public Settings get() {
		return new Settings(catalog.defaultTier(), javaVersion(), language());
	}

	/** Substitui as três preferências de uma vez. {@code defaultJavaVersion = null} volta ao automático. */
	@Transactional
	public Settings update(Settings settings) {
		save(ModelCatalogService.DEFAULT_TIER_SETTING, settings.defaultTier().value());
		if (settings.defaultJavaVersion() == null) {
			repository.deleteById(DEFAULT_JAVA_VERSION_KEY);
		}
		else {
			save(DEFAULT_JAVA_VERSION_KEY, String.valueOf(settings.defaultJavaVersion()));
		}
		save(LANGUAGE_KEY, settings.language().value());
		log.info("Preferências salvas: tier padrão {}, Java {}, idioma {}", settings.defaultTier().value(),
				settings.defaultJavaVersion() == null ? "automático" : settings.defaultJavaVersion(),
				settings.language().value());
		return settings;
	}

	private void save(String key, String value) {
		AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
		setting.setValue(value);
		repository.save(setting);
	}

	private Integer javaVersion() {
		Optional<String> stored = repository.findById(DEFAULT_JAVA_VERSION_KEY).map(AppSetting::getValue);
		try {
			return stored.map(Integer::valueOf).orElse(null);
		}
		catch (NumberFormatException e) {
			log.warn("Versão padrão do Java inválida no banco ('{}'); usando automático.", stored.orElse(""));
			return null;
		}
	}

	private Language language() {
		return repository.findById(LANGUAGE_KEY)
			.map(AppSetting::getValue)
			.flatMap(Language::find)
			.orElse(Language.PT_BR);
	}
}
