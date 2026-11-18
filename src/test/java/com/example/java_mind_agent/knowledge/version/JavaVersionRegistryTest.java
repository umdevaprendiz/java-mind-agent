package com.example.java_mind_agent.knowledge.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** O arquivo de dados {@code knowledge/java-versions.json} e as regras do registro. */
class JavaVersionRegistryTest {

	private final JavaVersionRegistry registry = new JavaVersionRegistry();

	@Test
	void temTodasAsVersoesDaUmAteAMaisNovaSemBuracos() {
		List<Integer> versoes = registry.all().stream().map(JavaRelease::version).toList();

		assertThat(versoes).containsExactlyElementsOf(IntStream.rangeClosed(1, 27).boxed().toList());
		assertThat(registry.latest().version()).isEqualTo(27);
	}

	@Test
	void asVersoesLtsSaoAsOficiais() {
		List<Integer> lts = registry.all().stream().filter(JavaRelease::lts).map(JavaRelease::version).toList();

		assertThat(lts).containsExactly(8, 11, 17, 21, 25);
		assertThat(registry.latestLts().version()).isEqualTo(25);
	}

	@Test
	void anosDeLancamentoBatemComOCalendarioConhecido() {
		assertThat(registry.find(1).orElseThrow().releaseYear()).isEqualTo(1996);
		assertThat(registry.find(5).orElseThrow().releaseYear()).isEqualTo(2004);
		assertThat(registry.find(8).orElseThrow().releaseYear()).isEqualTo(2014);
		assertThat(registry.find(11).orElseThrow().releaseYear()).isEqualTo(2018);
		assertThat(registry.find(17).orElseThrow().releaseYear()).isEqualTo(2021);
		assertThat(registry.find(21).orElseThrow().releaseYear()).isEqualTo(2023);
		assertThat(registry.find(25).orElseThrow().releaseYear()).isEqualTo(2025);
	}

	@Test
	void nomesSeguemOPadraoDaTela() {
		assertThat(registry.find(1).orElseThrow().label()).isEqualTo("Java 1.0 e 1.1");
		assertThat(registry.find(4).orElseThrow().label()).isEqualTo("Java 1.4");
		for (JavaRelease release : registry.all()) {
			if (release.version() >= 5) {
				assertThat(release.label()).isEqualTo("Java " + release.version());
			}
		}
	}

	@Test
	void cadaVersaoTemRecursosCurtosParaOSeletor() {
		for (JavaRelease release : registry.all()) {
			assertThat(release.highlights()).as(release.label()).isNotEmpty().hasSizeLessThanOrEqualTo(6);
			assertThat(release.highlights()).as(release.label()).allSatisfy(h -> assertThat(h).isNotBlank().hasSizeLessThan(100));
			assertThat(release.removed()).as(release.label()).allSatisfy(r -> assertThat(r).isNotBlank());
			assertThat(release.deprecated()).as(release.label()).allSatisfy(d -> assertThat(d).isNotBlank());
		}
	}

	@Test
	void osRecursosMarcantesEstaoNasVersoesCertas() {
		assertThat(registry.find(8).orElseThrow().highlights()).anyMatch(h -> h.contains("Lambdas"));
		assertThat(registry.find(16).orElseThrow().highlights()).anyMatch(h -> h.contains("Records"));
		assertThat(registry.find(17).orElseThrow().highlights()).anyMatch(h -> h.contains("Sealed"));
		assertThat(registry.find(21).orElseThrow().highlights()).anyMatch(h -> h.contains("Virtual threads"));
		assertThat(registry.find(11).orElseThrow().removed()).anyMatch(r -> r.contains("JAXB"));
	}

	@Test
	void infosTemOFormatoDaApiEUmaVersaoNaoRegistradaNaoExiste() {
		JavaVersionInfo info = registry.find(21).orElseThrow().info();

		assertThat(info.version()).isEqualTo(21);
		assertThat(info.label()).isEqualTo("Java 21");
		assertThat(info.lts()).isTrue();
		assertThat(registry.infos()).hasSize(27).first().extracting(JavaVersionInfo::version).isEqualTo(1);
		assertThat(registry.find(28)).isEmpty();
		assertThat(registry.contains(28)).isFalse();
		assertThat(registry.contains(8)).isTrue();
	}

	@Test
	void umArquivoEstragadoFalhaNaHora() {
		JavaRelease a = release(8, 2014, true);
		JavaRelease b = release(9, 2017, false);

		assertThatThrownBy(() -> new JavaVersionRegistry(List.of())).hasMessageContaining("vazio");
		assertThatThrownBy(() -> new JavaVersionRegistry(List.of(b, a))).hasMessageContaining("ordem crescente");
		assertThatThrownBy(() -> new JavaVersionRegistry(List.of(a, a))).hasMessageContaining("sem repetição");
		assertThatThrownBy(() -> new JavaVersionRegistry(List.of(b, release(10, 2018, false)))).hasMessageContaining("LTS");
		assertThatThrownBy(() -> new JavaVersionRegistry(List.of(a, release(9, 2010, false)))).hasMessageContaining("mais antigo");
	}

	@Test
	void umaVersaoMalFormadaNaoEntra() {
		assertThatThrownBy(() -> new JavaRelease(0, "Java 0", 2000, false, List.of("x"), List.of(), List.of()))
			.hasMessageContaining("1 a 99");
		assertThatThrownBy(() -> new JavaRelease(8, " ", 2014, true, List.of("x"), List.of(), List.of()))
			.hasMessageContaining("sem nome");
		assertThatThrownBy(() -> new JavaRelease(8, "Java 8", 2014, true, List.of(), List.of(), List.of()))
			.hasMessageContaining("recursos marcantes");
	}

	private static JavaRelease release(int version, int year, boolean lts) {
		return new JavaRelease(version, "Java " + version, year, lts, List.of("algo"), List.of(), List.of());
	}
}
