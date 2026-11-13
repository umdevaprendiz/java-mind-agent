package com.example.java_mind_agent.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Só a interpretação dos nomes de placa de vídeo; perguntar ao sistema operacional fica fora dos testes. */
class GpuProbeTest {

	@ParameterizedTest
	@ValueSource(strings = { "NVIDIA GeForce RTX 3060", "NVIDIA RTX A4000", "GeForce GTX 1650", "Quadro T1000",
			"AMD Radeon RX 7800 XT", "AMD Radeon PRO W7800", "Apple M2 Pro" })
	void placasQueAceleramOOllama(String nome) {
		assertThat(GpuProbe.isAccelerated(nome)).as(nome).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Intel(R) UHD Graphics", "Intel(R) Iris(R) Xe Graphics", "Intel(R) Arc(TM) A770",
			"AMD Radeon(TM) Graphics", "AMD Radeon Vega 8 Graphics", "Microsoft Basic Display Adapter", "" })
	void graficosIntegradosEAdaptadoresBasicosNaoAceleram(String nome) {
		assertThat(GpuProbe.isAccelerated(nome)).as(nome).isFalse();
	}

	@Test
	void semNomeNaoAcelera() {
		assertThat(GpuProbe.isAccelerated(null)).isFalse();
	}

	@Test
	void primariaPrefereAPlacaQueAceleraSobreAIntegrada() {
		assertThat(GpuProbe.primary(List.of("Intel(R) UHD Graphics", "NVIDIA GeForce RTX 3060")))
			.isEqualTo("NVIDIA GeForce RTX 3060");
	}

	@Test
	void primariaSemAceleradaFicaComAPrimeiraReal() {
		assertThat(GpuProbe.primary(List.of("Intel(R) UHD Graphics", "AMD Radeon(TM) Graphics")))
			.isEqualTo("Intel(R) UHD Graphics");
	}

	@Test
	void primariaIgnoraAdaptadoresVirtuaisEEntradasEmBranco() {
		assertThat(GpuProbe.primary(List.of("  ", "Microsoft Basic Display Adapter", "Parsec Virtual Display",
				"Intel(R) UHD Graphics")))
			.isEqualTo("Intel(R) UHD Graphics");
	}

	@Test
	void primariaSemNadaDevolveNulo() {
		assertThat(GpuProbe.primary(List.of())).isNull();
		assertThat(GpuProbe.primary(List.of("Microsoft Basic Display Adapter"))).isNull();
	}

	@Test
	void leOsNomesDaSaidaDoSystemProfilerDoMac() {
		String saida = """
				Graphics/Displays:

				    Apple M2:

				      Chipset Model: Apple M2
				      Type: GPU
				      Bus: Built-In
				      Total Number of Cores: 10
				""";

		assertThat(GpuProbe.parseMacProfiler(saida)).containsExactly("Apple M2");
	}

	@Test
	void leOsNomesDaSaidaDoLspciIgnorandoOQueNaoEPlacaDeVideo() {
		String saida = """
				00:00.0 Host bridge: Intel Corporation Device 9b61 (rev 0c)
				00:02.0 VGA compatible controller: Intel Corporation UHD Graphics 620 (rev 07)
				01:00.0 3D controller: NVIDIA Corporation GP107M [GeForce GTX 1050 Mobile] (rev a1)
				02:00.0 Network controller: Intel Corporation Wireless 8265 (rev 78)
				""";

		assertThat(GpuProbe.parseLspci(saida)).containsExactly("Intel Corporation UHD Graphics 620",
				"NVIDIA Corporation GP107M [GeForce GTX 1050 Mobile]");
	}

	@Test
	void detectarNaMaquinaNuncaQuebra() {
		assertThat(GpuProbe.detectNames()).isNotNull();
	}
}
