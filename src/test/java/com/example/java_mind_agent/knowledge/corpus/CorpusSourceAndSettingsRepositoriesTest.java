package com.example.java_mind_agent.knowledge.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.java_mind_agent.common.persistence.AppSetting;
import com.example.java_mind_agent.common.persistence.AppSettingRepository;
import com.example.java_mind_agent.support.RepositoryIntegrationTest;

@RepositoryIntegrationTest
class CorpusSourceAndSettingsRepositoriesTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private CorpusSourceRepository sources;

	@Autowired
	private AppSettingRepository settings;

	@Test
	void salvaFonteEncontraPorNomeEFiltraPorStatus() {
		CorpusSource fonte = new CorpusSource("Javadoc Java 21", CorpusSourceKind.JAVADOC,
				"https://docs.oracle.com/en/java/javase/21/docs/api/");
		fonte.setJavaVersionMin(21);
		fonte.setJavaVersionMax(21);
		fonte.setStatus(CorpusSourceStatus.READY);
		fonte.setChunkCount(1200);
		sources.saveAndFlush(fonte);
		sources.saveAndFlush(new CorpusSource("JEPs", CorpusSourceKind.JEP, "https://openjdk.org/jeps/"));
		em.clear();

		assertThat(sources.findByName("Javadoc Java 21")).get()
				.extracting(CorpusSource::getChunkCount).isEqualTo(1200);
		assertThat(sources.findByStatus(CorpusSourceStatus.READY)).extracting(CorpusSource::getName)
				.containsExactly("Javadoc Java 21");
		assertThat(sources.findByStatus(CorpusSourceStatus.PENDING)).extracting(CorpusSource::getName)
				.containsExactly("JEPs");
	}

	@Test
	void nomeDaFonteEUnico() {
		sources.saveAndFlush(new CorpusSource("JEPs", CorpusSourceKind.JEP, "https://openjdk.org/jeps/"));

		assertThatThrownBy(() -> sources
				.saveAndFlush(new CorpusSource("JEPs", CorpusSourceKind.JEP, "https://outro.exemplo/")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejeitaFaixaDeVersaoInvertida() {
		CorpusSource fonte = new CorpusSource("JLS", CorpusSourceKind.JLS, "https://docs.oracle.com/javase/specs/");
		fonte.setJavaVersionMin(17);
		fonte.setJavaVersionMax(8);

		assertThatThrownBy(() -> sources.saveAndFlush(fonte)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void gravaEAtualizaConfiguracoes() {
		settings.saveAndFlush(new AppSetting("default.tier", "medium"));
		em.clear();

		AppSetting lida = settings.findById("default.tier").orElseThrow();
		assertThat(lida.getValue()).isEqualTo("medium");
		assertThat(lida.getUpdatedAt()).isNotNull();

		lida.setValue("strong");
		settings.saveAndFlush(lida);
		em.clear();

		assertThat(settings.findById("default.tier")).get().extracting(AppSetting::getValue).isEqualTo("strong");
	}
}
