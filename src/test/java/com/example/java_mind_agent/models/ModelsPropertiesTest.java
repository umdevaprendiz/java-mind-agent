package com.example.java_mind_agent.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.example.java_mind_agent.models.ModelsProperties.Capacity;
import com.example.java_mind_agent.models.ModelsProperties.Debug;
import com.example.java_mind_agent.models.ModelsProperties.Generation;
import com.example.java_mind_agent.models.ModelsProperties.TierProperties;
import com.example.java_mind_agent.support.ModelsTestData;

/** A configuração do módulo: o {@code application.yml} de verdade tem de ligar nos quatro tiers, e o que for inválido falha cedo. */
class ModelsPropertiesTest {

	@Configuration
	@EnableConfigurationProperties(ModelsProperties.class)
	static class Config {
	}

	@Test
	void oApplicationYmlDefineOsQuatroTiersComOsModelosEsperados() {
		new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
			.withUserConfiguration(Config.class)
			.run(contexto -> {
				assertThat(contexto).hasNotFailed();
				ModelsProperties p = contexto.getBean(ModelsProperties.class);

				assertThat(p.defaultTier()).isEqualTo(Tier.MEDIUM);
				assertThat(p.tiers()).containsOnlyKeys(Tier.values());
				assertThat(p.tiers().get(Tier.LOW).ollamaModel()).isEqualTo("qwen2.5:3b");
				assertThat(p.tiers().get(Tier.MEDIUM).ollamaModel()).isEqualTo("qwen2.5:7b");
				assertThat(p.tiers().get(Tier.STRONG).ollamaModel()).isEqualTo("qwen2.5:14b");
				assertThat(p.tiers().get(Tier.EXTRA).ollamaModel()).isEqualTo("qwen3:32b");
				assertThat(p.tiers().values()).extracting(TierProperties::label)
					.containsExactlyInAnyOrder("Baixo", "Médio", "Forte", "Extra");
				assertThat(p.tiers().values()).allSatisfy(t -> {
					assertThat(t.description()).isNotBlank();
					assertThat(t.minRamGb()).isPositive();
					assertThat(t.baseUrl()).as("todos no Ollama padrão até alguém configurar outro").isNull();
				});
				assertThat(p.tiers().get(Tier.EXTRA).minRamGb()).as("não cabe em 16 GB").isGreaterThan(16);
				assertThat(p.tiers().get(Tier.LOW).speed()).isEqualTo(Speed.FAST);
				assertThat(p.tiers().get(Tier.EXTRA).speed()).isEqualTo(Speed.VERY_SLOW);
				assertThat(p.capacity()).isEqualTo(new Capacity(1.3, 3));
				assertThat(p.embedding().model()).isEqualTo("bge-m3");
				assertThat(p.generation().temperature()).isEqualTo(0.3);
				assertThat(p.generation().numCtx()).isEqualTo(4096);
				assertThat(p.generation().firstTokenTimeout()).isEqualTo(Duration.ofMinutes(3));
			});
	}

	@Test
	void faltandoUmTierAAplicacaoNaoSobe() {
		Map<Tier, TierProperties> semExtra = ModelsTestData.tiers();
		semExtra.remove(Tier.EXTRA);

		assertThatThrownBy(() -> new ModelsProperties(Tier.MEDIUM, generation(), new Capacity(1.3, 3), semExtra,
				ModelsTestData.embedding(null), false, new Debug(false)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("quatro níveis")
			.hasMessageContaining("extra");
	}

	@Test
	void semNenhumTierTambemFalhaEmVezDeSubirVazio() {
		assertThatThrownBy(() -> new ModelsProperties(Tier.MEDIUM, generation(), new Capacity(1.3, 3), null,
				ModelsTestData.embedding(null), false, new Debug(false)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("low")
			.hasMessageContaining("strong");
	}

	@Test
	void tierPrecisaDeLabelModeloEMemoriaPositiva() {
		assertThatThrownBy(() -> new TierProperties(" ", "d", "qwen2.5:3b", 2.5, Speed.FAST, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TierProperties("Baixo", "d", "", 2.5, Speed.FAST, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TierProperties("Baixo", "d", "qwen2.5:3b", 0, Speed.FAST, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Baixo");
	}

	@Test
	void baseUrlEmBrancoViraNuloEAsDemaisSaemSemEspacos() {
		assertThat(new TierProperties("Extra", "d", "m", 26, Speed.VERY_SLOW, "  ").baseUrl()).isNull();
		assertThat(new TierProperties("Extra", "d", "m", 26, Speed.VERY_SLOW, " http://gpu:11434 ").baseUrl())
			.isEqualTo("http://gpu:11434");
		assertThat(new ModelsProperties.Embedding("E", "d", "bge-m3", 1.5, "").baseUrl()).isNull();
	}

	@Test
	void osTiersFicamImutaveisDepoisDeCriadas() {
		Map<Tier, TierProperties> origem = new EnumMap<>(ModelsTestData.tiers());
		ModelsProperties p = ModelsTestData.properties(Tier.MEDIUM, origem, ModelsTestData.embedding(null));
		origem.remove(Tier.LOW);

		assertThat(p.tiers()).containsKey(Tier.LOW);
		assertThatThrownBy(() -> p.tiers().remove(Tier.LOW)).isInstanceOf(UnsupportedOperationException.class);
	}

	private static Generation generation() {
		return new Generation(0.3, 4096, "10m", Duration.ofMinutes(3), Duration.ofMinutes(1), Duration.ZERO);
	}
}
