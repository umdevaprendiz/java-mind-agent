package com.example.java_mind_agent.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.java_mind_agent.common.persistence.AppSetting;
import com.example.java_mind_agent.common.persistence.AppSettingRepository;
import com.example.java_mind_agent.models.catalog.ModelCatalogService;
import com.example.java_mind_agent.models.hardware.HardwareInfo;
import com.example.java_mind_agent.models.ollama.OllamaHosts;
import com.example.java_mind_agent.models.Tier;
import com.example.java_mind_agent.support.ModelsTestData;
import com.example.java_mind_agent.support.RepositoryIntegrationTest;

/**
 * As preferências gravadas em {@code app_setting} de verdade (Postgres via Testcontainers) e lidas de volta pela mesma
 * fonte que o roteador e o catálogo usam para o tier padrão.
 */
@RepositoryIntegrationTest
class SettingsServiceDbTest {

	@Autowired
	private AppSettingRepository repositorio;

	@Autowired
	private TestEntityManager em;

	private ModelCatalogService catalogo;

	private SettingsService servico;

	@BeforeEach
	void preparaServico() {
		repositorio.deleteAll();
		em.flush();
		catalogo = new ModelCatalogService(ModelsTestData.properties(), new OllamaHosts("http://localhost:1"),
				() -> new HardwareInfo(15.76, 2.5, "Intel(R) UHD Graphics", true), repositorio);
		servico = new SettingsService(repositorio, catalogo);
	}

	@Test
	void semNadaGravadoValemOsPadroesDaConfiguracao() {
		assertThat(servico.get()).isEqualTo(new Settings(Tier.MEDIUM, null, Language.PT_BR));
	}

	@Test
	void gravaAsTresPreferenciasELeDeVolta() {
		servico.update(new Settings(Tier.STRONG, 17, Language.EN));
		em.flush();
		em.clear();

		assertThat(servico.get()).isEqualTo(new Settings(Tier.STRONG, 17, Language.EN));
		assertThat(valor("default.tier")).isEqualTo("strong");
		assertThat(valor("default.java-version")).isEqualTo("17");
		assertThat(valor("language")).isEqualTo("en");
	}

	@Test
	void oTierGravadoEOQueOCatalogoEORoteadorEnxergamComoPadrao() {
		servico.update(new Settings(Tier.LOW, null, Language.PT_BR));
		em.flush();
		em.clear();

		assertThat(catalogo.defaultTier()).isEqualTo(Tier.LOW);
	}

	@Test
	void gravarDeNovoSubstituiEJavaNullApagaAPreferencia() {
		servico.update(new Settings(Tier.STRONG, 17, Language.EN));
		em.flush();
		em.clear();

		servico.update(new Settings(Tier.MEDIUM, null, Language.PT_BR));
		em.flush();
		em.clear();

		assertThat(servico.get()).isEqualTo(new Settings(Tier.MEDIUM, null, Language.PT_BR));
		assertThat(repositorio.findById("default.java-version")).isEmpty();
		assertThat(repositorio.count()).isEqualTo(2);
	}

	@Test
	void gravarJavaAutomaticoSemTerGravadoNadaAntesNaoDaErro() {
		servico.update(new Settings(Tier.LOW, null, Language.PT_BR));
		em.flush();

		assertThat(repositorio.findById("default.java-version")).isEmpty();
	}

	@Test
	void valorEstragadoNoBancoNaoQuebraALeitura() {
		repositorio.saveAndFlush(new AppSetting("default.tier", "huge"));
		repositorio.saveAndFlush(new AppSetting("default.java-version", "vinte"));
		repositorio.saveAndFlush(new AppSetting("language", "fr"));
		em.clear();

		assertThat(servico.get()).as("tier da configuração, Java automático, português")
			.isEqualTo(new Settings(Tier.MEDIUM, null, Language.PT_BR));
	}

	private String valor(String chave) {
		return repositorio.findById(chave).orElseThrow().getValue();
	}
}
