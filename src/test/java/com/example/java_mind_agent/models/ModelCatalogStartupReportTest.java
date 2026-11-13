package com.example.java_mind_agent.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.models.ModelCatalogStartupReport.Line;
import com.example.java_mind_agent.models.ModelInfo.Kind;
import com.example.java_mind_agent.models.ModelInfo.UnavailableReason;

/** O texto que aparece no log ao subir: só o que está errado vira aviso, e o aviso diz o que fazer. */
class ModelCatalogStartupReportTest {

	private static final HardwareInfo MAQUINA = new HardwareInfo(15.76, 2.5, "Intel(R) UHD Graphics", true);

	private static ModelInfo modelo(String id, String label, String ollama, double minRam, boolean cabe, boolean instalado,
			UnavailableReason motivo, String remoto) {
		return new ModelInfo(id, Kind.CHAT, label, "d", ollama, remoto, null, minRam, instalado, false, cabe, null,
				motivo == null, motivo, false, Speed.SLOW, null, null);
	}

	private static ModelCatalog catalogo(HardwareInfo hw, ModelInfo... tiers) {
		return new ModelCatalog(hw, Tier.MEDIUM, List.of(tiers), new ModelInfo("embedding", Kind.EMBEDDING, "Embeddings",
				"d", "bge-m3", null, null, 1.5, true, false, true, null, true, null, true, Speed.FAST, null, null));
	}

	@Test
	void descreveOHardwareSemAvisoEAvisaOQueNaoCabe() {
		ModelCatalog catalogo = catalogo(MAQUINA,
				modelo("medium", "Médio", "qwen2.5:7b", 6, true, true, null, null),
				modelo("extra", "Extra", "qwen3:32b", 26, false, false, UnavailableReason.DOES_NOT_FIT, null));

		List<Line> linhas = ModelCatalogStartupReport.describe(catalogo, 3);

		assertThat(linhas.get(0).warning()).isFalse();
		assertThat(linhas.get(0).text()).contains("15,8 GB de RAM").contains("2,5 GB livres")
			.contains("Intel(R) UHD Graphics").contains("na CPU");
		assertThat(linhas.get(1).text()).isEqualTo("Médio (qwen2.5:7b): disponível");
		assertThat(linhas.get(1).warning()).isFalse();
		assertThat(linhas.get(2).warning()).isTrue();
		assertThat(linhas.get(2).text()).isEqualTo("Extra (qwen3:32b): INVIÁVEL nesta máquina — pede ~26,0 GB e há "
				+ "12,8 GB utilizáveis (RAM total 15,8 GB, reserva 3,0 GB). Aponte base-url para outro Ollama para usá-lo.");
	}

	@Test
	void semPlacaIdentificadaDizQueNaoIdentificou() {
		ModelCatalog catalogo = catalogo(new HardwareInfo(8, 2, null, true));

		assertThat(ModelCatalogStartupReport.describe(catalogo, 3).get(0).text()).contains("GPU: não identificada");
	}

	@Test
	void placaComAceleracaoNaoDizQueRodaNaCpu() {
		ModelCatalog catalogo = catalogo(new HardwareInfo(32, 20, "NVIDIA GeForce RTX 4090", false));

		assertThat(ModelCatalogStartupReport.describe(catalogo, 3).get(0).text()).contains("com aceleração")
			.doesNotContain("na CPU");
	}

	@Test
	void naoInstaladoEInformativoENaoAviso() {
		ModelCatalog catalogo = catalogo(MAQUINA,
				modelo("strong", "Forte", "qwen2.5:14b", 12, true, false, UnavailableReason.NOT_INSTALLED, null));

		Line linha = ModelCatalogStartupReport.describe(catalogo, 3).get(1);

		assertThat(linha.warning()).isFalse();
		assertThat(linha.text()).isEqualTo("Forte (qwen2.5:14b): ainda não instalado (ollama pull qwen2.5:14b)");
	}

	@Test
	void ollamaForaDoArEAvisoComOHostQuandoERemoto() {
		ModelCatalog catalogo = catalogo(MAQUINA,
				modelo("low", "Baixo", "qwen2.5:3b", 2.5, true, false, UnavailableReason.HOST_UNREACHABLE, null),
				modelo("extra", "Extra", "qwen3:32b", 26, true, false, UnavailableReason.HOST_UNREACHABLE,
						"http://gpu-server:11434"));

		List<Line> linhas = ModelCatalogStartupReport.describe(catalogo, 3);

		assertThat(linhas.get(1)).isEqualTo(new Line(true, "Baixo (qwen2.5:3b): Ollama fora do ar (host padrão)"));
		assertThat(linhas.get(2)).isEqualTo(
				new Line(true, "Extra (qwen3:32b): Ollama fora do ar (http://gpu-server:11434)"));
	}

	@Test
	void quemNaoCabeContinuaInviavelMesmoComOOllamaDesligado() {
		ModelCatalog catalogo = catalogo(MAQUINA,
				modelo("extra", "Extra", "qwen3:32b", 26, false, false, UnavailableReason.HOST_UNREACHABLE, null));

		Line linha = ModelCatalogStartupReport.describe(catalogo, 3).get(1);

		assertThat(linha.warning()).isTrue();
		assertThat(linha.text()).contains("INVIÁVEL nesta máquina");
	}

	@Test
	void tierRemotoDisponivelDizEmQualOllamaEsta() {
		ModelCatalog catalogo = catalogo(MAQUINA,
				modelo("extra", "Extra", "qwen3:32b", 26, true, true, null, "http://gpu-server:11434"));

		assertThat(ModelCatalogStartupReport.describe(catalogo, 3).get(1).text())
			.isEqualTo("Extra (qwen3:32b): disponível, no Ollama http://gpu-server:11434");
	}

	@Test
	void incluiOModeloDeEmbeddingNoFinal() {
		List<Line> linhas = ModelCatalogStartupReport.describe(catalogo(MAQUINA), 3);

		assertThat(linhas.get(linhas.size() - 1).text()).isEqualTo("Embeddings (bge-m3): disponível");
	}
}
