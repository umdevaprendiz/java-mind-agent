package com.example.java_mind_agent.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.java_mind_agent.support.RepositoryIntegrationTest;

@RepositoryIntegrationTest
class PdfRepositoriesTest {

	private static final String HASH = "b".repeat(64);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private PdfDocumentRepository pdfs;

	@Autowired
	private PdfJobRepository jobs;

	@Autowired
	private QuestionRepository questions;

	@Autowired
	private AnswerRepository answers;

	@Test
	void encontraOPdfPeloHashDoConteudo() {
		pdfs.saveAndFlush(novoPdf("prova.pdf", HASH));
		em.clear();

		assertThat(pdfs.findBySha256(HASH)).get().extracting(PdfDocument::getFilename).isEqualTo("prova.pdf");
		assertThat(pdfs.findBySha256("c".repeat(64))).isEmpty();
	}

	@Test
	void naoPermiteDoisPdfsComOMesmoHash() {
		pdfs.saveAndFlush(novoPdf("a.pdf", HASH));

		assertThatThrownBy(() -> pdfs.saveAndFlush(novoPdf("b.pdf", HASH)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void guardaQuestoesNaOrdemDeLeituraERespostaComMetadados() {
		PdfDocument pdf = pdfs.save(novoPdf("prova.pdf", HASH));
		Question segunda = new Question(pdf, 2, QuestionType.TRUE_FALSE, "Todo record é final?");
		Question primeira = new Question(pdf, 1, QuestionType.MULTIPLE_CHOICE, "O que o código imprime?");
		primeira.setLabel("1");
		primeira.setCode("System.out.println(1 + 1);");
		primeira.setAlternatives("[{\"label\":\"a\",\"text\":\"2\"},{\"label\":\"b\",\"text\":\"11\"}]");
		primeira.setJavaVersion(17);
		primeira.setNeedsReview(true);
		questions.saveAll(List.of(segunda, primeira));

		Answer resposta = new Answer(primeira, AnswerStatus.COMPLETED);
		resposta.setChosenAlternative("a");
		resposta.setConfidence(0.85);
		resposta.setVerifiedByExecution(true);
		resposta.setExecutionOutput("2");
		resposta.setSources("[{\"title\":\"JLS 15.18\"}]");
		answers.save(resposta);
		em.flush();
		em.clear();

		List<Question> lidas = questions.findByPdfDocumentIdOrderByOrdinalAsc(pdf.getId());
		Answer respostaLida = answers.findFirstByQuestionIdOrderByCreatedAtDesc(primeira.getId()).orElseThrow();

		assertThat(lidas).extracting(Question::getOrdinal).containsExactly(1, 2);
		assertThat(lidas.get(0).isNeedsReview()).isTrue();
		assertThat(lidas.get(0).getAlternatives()).contains("\"label\"");
		assertThat(respostaLida.getChosenAlternative()).isEqualTo("a");
		assertThat(respostaLida.getConfidence()).isEqualTo(0.85);
		assertThat(respostaLida.isVerifiedByExecution()).isTrue();
	}

	@Test
	void naoPermiteDuasQuestoesComAMesmaPosicaoNoPdf() {
		PdfDocument pdf = pdfs.save(novoPdf("prova.pdf", HASH));
		questions.saveAndFlush(new Question(pdf, 1, QuestionType.ESSAY, "Explique o GC."));

		assertThatThrownBy(() -> questions.saveAndFlush(new Question(pdf, 1, QuestionType.ESSAY, "Outra")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejeitaConfiancaForaDoIntervaloDeZeroAUm() {
		PdfDocument pdf = pdfs.save(novoPdf("prova.pdf", HASH));
		Question questao = questions.save(new Question(pdf, 1, QuestionType.ESSAY, "Explique o GC."));
		Answer resposta = new Answer(questao, AnswerStatus.COMPLETED);
		resposta.setConfidence(1.5);

		assertThatThrownBy(() -> answers.saveAndFlush(resposta)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void encontraLotesInterrompidosParaRetomada() {
		PdfDocument pdf = pdfs.save(novoPdf("prova.pdf", HASH));
		PdfJob emAndamento = new PdfJob(pdf, "medium");
		emAndamento.setStatus(JobStatus.RUNNING);
		PdfJob concluido = new PdfJob(pdf, "strong");
		concluido.setStatus(JobStatus.COMPLETED);
		jobs.saveAll(List.of(emAndamento, concluido));
		em.flush();
		em.clear();

		List<PdfJob> retomaveis = jobs.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING, JobStatus.PAUSED));

		assertThat(retomaveis).extracting(PdfJob::getTier).containsExactly("medium");
		assertThat(jobs.findByPdfDocumentIdOrderByCreatedAtDesc(pdf.getId())).hasSize(2);
	}

	@Test
	void apagarOPdfRemoveLotesQuestoesERespostas() {
		PdfDocument pdf = pdfs.save(novoPdf("prova.pdf", HASH));
		PdfJob lote = jobs.save(new PdfJob(pdf, "medium"));
		Question questao = questions.save(new Question(pdf, 1, QuestionType.ESSAY, "Explique o GC."));
		Answer resposta = new Answer(questao, AnswerStatus.COMPLETED);
		resposta.setJob(lote);
		answers.save(resposta);
		em.flush();
		em.clear();

		pdfs.deleteById(pdf.getId());
		em.flush();
		em.clear();

		assertThat(jobs.count()).isZero();
		assertThat(questions.count()).isZero();
		assertThat(answers.count()).isZero();
	}

	private static PdfDocument novoPdf(String nome, String hash) {
		return new PdfDocument(nome, 2048, hash, "data/pdfs/" + nome);
	}
}
