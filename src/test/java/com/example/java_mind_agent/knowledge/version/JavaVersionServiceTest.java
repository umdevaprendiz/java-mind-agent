package com.example.java_mind_agent.knowledge.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.java_mind_agent.knowledge.KnowledgeProperties;
import com.example.java_mind_agent.settings.Language;

/** A política de versão: citada &gt; escolhida &gt; inferida do código &gt; preferência &gt; LTS mais recente. */
class JavaVersionServiceTest {

	private static final String PERGUNTA_SEM_VERSAO = "Qual a diferença entre ArrayList e LinkedList?";

	private static final String CODIGO_COM_RECORD = "record P(int x) { }";

	private final JavaVersionRegistry registry = new JavaVersionRegistry();

	private final JavaVersionService service = service(null, 8);

	private JavaVersionService service(Integer padrao, int antiga) {
		return new JavaVersionService(registry, new JavaVersionDetector(registry), new KnowledgeProperties(padrao, antiga));
	}

	@Test
	void semNenhumaPistaUsaALtsMaisRecente() {
		AssumedVersion v = service.assume(PERGUNTA_SEM_VERSAO, null, null);

		assertThat(v.version()).isEqualTo(25);
		assertThat(v.label()).isEqualTo("Java 25");
		assertThat(v.source()).isEqualTo(VersionSource.DEFAULT);
		assertThat(v.orNewer()).isFalse();
		assertThat(service.assume(null, null, null).version()).isEqualTo(25);
	}

	@Test
	void oQueAPessoaDisseVenceTudo() {
		AssumedVersion v = service.assume("No Java 11, como uso " + CODIGO_COM_RECORD + "?", 21, 17);

		assertThat(v.version()).isEqualTo(11);
		assertThat(v.source()).isEqualTo(VersionSource.MENTIONED);
	}

	@Test
	void comVariasVersoesCitadasUsaAMaisNovaEGuardaTodas() {
		AssumedVersion v = service.assume("O que mudou do Java 8 para o 17?", null, null);

		assertThat(v.version()).isEqualTo(17);
		assertThat(v.mentioned()).containsExactly(8, 17);
		assertThat(v.statement(Language.PT_BR)).contains("você citou Java 8, Java 17; uso a mais nova");
	}

	@Test
	void versaoAntigaSemNumeroUsaAConfigurada() {
		assertThat(service.assume("preciso de uma versão antiga", null, null))
			.extracting(AssumedVersion::version, AssumedVersion::source)
			.containsExactly(8, VersionSource.LEGACY_HINT);
		assertThat(service(null, 7).assume("preciso de uma versão antiga", null, null).version()).isEqualTo(7);
	}

	@Test
	void ultimaVersaoUsaAMaisNovaDoRegistroMesmoQueNaoSejaLts() {
		AssumedVersion v = service.assume("qual a última versão do Java?", 17, null);

		assertThat(v.version()).isEqualTo(27);
		assertThat(v.source()).isEqualTo(VersionSource.LATEST_HINT);
	}

	@Test
	void aVersaoEscolhidaVenceOCodigoEAvisaOConflito() {
		AssumedVersion v = service.assume(CODIGO_COM_RECORD, 11, 21);

		assertThat(v.version()).isEqualTo(11);
		assertThat(v.source()).isEqualTo(VersionSource.SELECTED);
		assertThat(v.conflicts()).extracting(VersionEvidence::rule).containsExactly("record");
		assertThat(v.statement(Language.PT_BR)).contains("Java 11").contains("Atenção").contains("record");
	}

	@Test
	void oCodigoVenceAPreferenciaEViraUmPiso() {
		AssumedVersion v = service.assume(CODIGO_COM_RECORD, null, 11);

		assertThat(v.version()).isEqualTo(16);
		assertThat(v.source()).isEqualTo(VersionSource.INFERRED);
		assertThat(v.orNewer()).isTrue();
		assertThat(v.evidence()).extracting(VersionEvidence::rule).containsExactly("record");
		assertThat(v.conflicts()).isEmpty();
		assertThat(v.statement(Language.PT_BR)).isEqualTo("Versão do Java assumida: Java 16 ou mais nova (o código usa record).");
	}

	@Test
	void aPreferenciaVenceOPadrao() {
		AssumedVersion v = service.assume(PERGUNTA_SEM_VERSAO, null, 17);

		assertThat(v.version()).isEqualTo(17);
		assertThat(v.source()).isEqualTo(VersionSource.SETTINGS);
	}

	@Test
	void versoesForaDoRegistroSaoIgnoradasEnaoQuebram() {
		assertThat(service.assume(PERGUNTA_SEM_VERSAO, 99, 98).source()).isEqualTo(VersionSource.DEFAULT);
		assertThat(service.assume("E no Java 40?", null, null).source()).isEqualTo(VersionSource.DEFAULT);
		assertThat(service.assume(PERGUNTA_SEM_VERSAO, 99, 17).source()).isEqualTo(VersionSource.SETTINGS);
	}

	@Test
	void umPadraoConfiguradoSubstituiALts() {
		JavaVersionService com21 = service(21, 8);

		assertThat(com21.defaultVersion()).isEqualTo(21);
		assertThat(com21.assume(PERGUNTA_SEM_VERSAO, null, null).version()).isEqualTo(21);
	}

	@Test
	void configuracaoComVersaoInexistenteFalhaNaSubida() {
		assertThatThrownBy(() -> service(99, 8)).isInstanceOf(IllegalStateException.class).hasMessageContaining("default-java-version");
		assertThatThrownBy(() -> service(null, 99)).isInstanceOf(IllegalStateException.class).hasMessageContaining("legacy-java-version");
	}

	@Test
	void aRespostaSempreDeclaraAVersaoNosDoisIdiomas() {
		assertThat(service.assume(PERGUNTA_SEM_VERSAO, null, null).statement(Language.PT_BR))
			.isEqualTo("Versão do Java assumida: Java 25 (nenhuma versão indicada; uso a LTS mais recente).");
		assertThat(service.assume(PERGUNTA_SEM_VERSAO, null, null).statement(Language.EN))
			.isEqualTo("Java version assumed: Java 25 (no version indicated; using the latest LTS).");
		assertThat(service.assume("no Java 8", null, null).statement(Language.PT_BR))
			.isEqualTo("Versão do Java assumida: Java 8 (citada na pergunta).");
		assertThat(service.assume("in Java 8", null, null).statement(Language.EN))
			.isEqualTo("Java version assumed: Java 8 (mentioned in the question).");
		assertThat(service.assume(PERGUNTA_SEM_VERSAO, 17, null).statement(Language.PT_BR)).contains("a versão escolhida para esta conversa");
		assertThat(service.assume(PERGUNTA_SEM_VERSAO, null, 17).statement(Language.PT_BR)).contains("sua preferência salva");
		assertThat(service.assume(CODIGO_COM_RECORD, null, null).statement(Language.EN))
			.isEqualTo("Java version assumed: Java 16 or newer (the code uses record).");
	}

	@Test
	void recursoEmPreviewAvisaDaFlag() {
		AssumedVersion v = service.assume("var s = StructuredTaskScope.open();", null, null);

		assertThat(v.previewFeatures()).extracting(VersionEvidence::rule).containsExactly("structured-concurrency");
		assertThat(v.statement(Language.PT_BR)).contains("ainda em preview").contains("--enable-preview");
		assertThat(v.statement(Language.EN)).contains("still a preview feature");
	}

	@Test
	void listaAsVersoesParaOSeletor() {
		assertThat(service.versions()).hasSize(27);
		assertThat(service.versions().get(26).label()).isEqualTo("Java 27");
	}
}
