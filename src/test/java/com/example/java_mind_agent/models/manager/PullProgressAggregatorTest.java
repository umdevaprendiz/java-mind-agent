package com.example.java_mind_agent.models.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.common.error.ErrorCode;
import com.example.java_mind_agent.models.ModelException;
import com.example.java_mind_agent.models.ollama.OllamaAdminClient.PullLine;

/** As linhas do {@code /api/pull} viram progresso somado e com ritmo controlado. O relógio é passado à mão. */
class PullProgressAggregatorTest {

	private static final long MS = 1_000_000L;

	private static PullProgressAggregator agregador(long intervaloMs) {
		return new PullProgressAggregator("qwen2.5:14b", Duration.ofMillis(intervaloMs));
	}

	private static PullLine etapa(String status) {
		return new PullLine(status, null, null, null, null);
	}

	private static PullLine camada(String digest, long total, long baixado) {
		return new PullLine("pulling " + digest.substring(7, 13), digest, total, baixado, null);
	}

	private static PullEvent.Progress progresso(Optional<PullEvent.Progress> evento) {
		return evento.orElseThrow(() -> new AssertionError("era esperado um evento de progresso"));
	}

	@Test
	void etapaSemTamanhoSaiSemBytesNemPorcentagem() {
		PullEvent.Progress p = progresso(agregador(0).accept(etapa("pulling manifest"), 0));

		assertThat(p).isEqualTo(new PullEvent.Progress("pulling manifest", null, null, null));
	}

	@Test
	void linhasDeCamadaViramDownloadingComBytesEPorcentagem() {
		PullEvent.Progress p = progresso(agregador(0).accept(camada("sha256:aaaaaaaaaaaa", 1000, 250), 0));

		assertThat(p).isEqualTo(new PullEvent.Progress("downloading", 250L, 1000L, 25.0));
	}

	@Test
	void somaAsCamadasEmUmTotalSo() {
		PullProgressAggregator agregador = agregador(0);
		agregador.accept(camada("sha256:aaaaaaaaaaaa", 800, 400), 0);

		PullEvent.Progress p = progresso(agregador.accept(camada("sha256:bbbbbbbbbbbb", 200, 0), 1));

		assertThat(p.completedBytes()).isEqualTo(400);
		assertThat(p.totalBytes()).isEqualTo(1000);
		assertThat(p.percent()).isEqualTo(40.0);
	}

	@Test
	void camadaQueAindaNaoComecouNaoTemCampoDeBaixado() {
		PullProgressAggregator agregador = agregador(0);

		PullEvent.Progress p = progresso(agregador.accept(new PullLine("pulling cccccc", "sha256:cccccccccccc", 500L,
				null, null), 0));

		assertThat(p).isEqualTo(new PullEvent.Progress("downloading", 0L, 500L, 0.0));
	}

	@Test
	void downloadRetomadoJaComecaDeOndeParou() {
		// Ollama guardou 500 de 1000 bytes de uma tentativa anterior: a primeira linha já diz 500.
		PullEvent.Progress p = progresso(agregador(0).accept(camada("sha256:aaaaaaaaaaaa", 1000, 500), 0));

		assertThat(p.percent()).isEqualTo(50.0);
	}

	@Test
	void porcentagemTemUmaCasaDecimal() {
		assertThat(progresso(agregador(0).accept(camada("sha256:aaaaaaaaaaaa", 3, 1), 0)).percent()).isEqualTo(33.3);
		assertThat(progresso(agregador(0).accept(camada("sha256:aaaaaaaaaaaa", 3, 2), 0)).percent()).isEqualTo(66.7);
	}

	@Test
	void baixadoNuncaRecuaNemPassaDoTotal() {
		PullProgressAggregator agregador = agregador(0);
		agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 600), 0);

		PullEvent.Progress recuou = progresso(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 300), 1));
		PullEvent.Progress estourou = progresso(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 5000), 2));

		assertThat(recuou.completedBytes()).isEqualTo(600);
		assertThat(estourou.completedBytes()).isEqualTo(1000);
		assertThat(estourou.percent()).isEqualTo(100.0);
	}

	@Test
	void limitaORitmoDosEventosDeProgresso() {
		PullProgressAggregator agregador = agregador(250);

		assertThat(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 100), 0)).isPresent();
		assertThat(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 200), 100 * MS)).isEmpty();
		assertThat(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 300), 249 * MS)).isEmpty();
		assertThat(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 400), 250 * MS)).isPresent();
	}

	@Test
	void oQueFoiPuladoPeloRitmoEntraNaProximaContagem() {
		PullProgressAggregator agregador = agregador(250);
		agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 100), 0);
		agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 900), 100 * MS);

		PullEvent.Progress p = progresso(agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 910), 300 * MS));

		assertThat(p.completedBytes()).isEqualTo(910);
	}

	@Test
	void mudancaDeEtapaSaiMesmoDentroDoIntervalo() {
		PullProgressAggregator agregador = agregador(250);
		agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 999), 0);

		PullEvent.Progress p = progresso(agregador.accept(etapa("verifying sha256 digest"), 10 * MS));

		assertThat(p.status()).isEqualTo("verifying sha256 digest");
		assertThat(p.percent()).as("a verificação mostra o total já baixado").isEqualTo(99.9);
	}

	@Test
	void linhasSemStatusENemCamadaSaoIgnoradas() {
		assertThat(agregador(0).accept(etapa(""), 0)).isEmpty();
		assertThat(agregador(0).accept(new PullLine(null, null, null, null, null), 0)).isEmpty();
	}

	@Test
	void successNaoViraEventoMasPermiteTerminar() {
		PullProgressAggregator agregador = agregador(0);

		assertThat(agregador.accept(etapa("success"), 0)).isEmpty();
		assertThat(agregador.finish()).isEqualTo(new PullEvent.Done());
	}

	@Test
	void streamQueAcabaSemSuccessNaoEhSucesso() {
		PullProgressAggregator agregador = agregador(0);
		agregador.accept(camada("sha256:aaaaaaaaaaaa", 1000, 400), 0);

		assertThatThrownBy(agregador::finish).isInstanceOfSatisfying(ModelException.class, e -> {
			assertThat(e.getCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
			assertThat(e.isRetryable()).isTrue();
			assertThat(e.getMessage()).contains("qwen2.5:14b").contains("sem a confirmação");
		});
	}

	@Test
	void linhaDeErroDoOllamaViraModelException() {
		PullProgressAggregator agregador = agregador(0);

		assertThatThrownBy(() -> agregador.accept(
				new PullLine(null, null, null, null, "pull model manifest: file does not exist"), 0))
			.isInstanceOfSatisfying(ModelException.class, e -> {
				assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
				assertThat(e.getMessage()).contains("qwen2.5:14b");
			});
	}

	@Test
	void erroEmBrancoNaoEhErro() {
		assertThat(agregador(0).accept(new PullLine("pulling manifest", null, null, null, ""), 0)).isPresent();
	}
}
